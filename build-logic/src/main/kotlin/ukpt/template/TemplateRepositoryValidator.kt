package ukpt.template

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.stream.Collectors
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A template validation failure associated with a repository-relative path. */
data class TemplateValidationIssue(
    val path: String,
    val message: String,
)

/**
 * A comparable representation of UKPT's date-based template version.
 *
 * Versions use `YYYY-MM-DD` with an optional positive revision suffix such as `.2`.
 */
data class TemplateVersion(
    val date: LocalDate,
    val revision: Int = 0,
) : Comparable<TemplateVersion> {
    override fun compareTo(other: TemplateVersion): Int =
        compareValuesBy(this, other, TemplateVersion::date, TemplateVersion::revision)

    companion object {
        private val pattern = Regex("""^(\d{4}-\d{2}-\d{2})(?:\.([1-9]\d*))?$""")

        /**
         * Parses a template version in `YYYY-MM-DD` or `YYYY-MM-DD.N` form.
         *
         * @throws IllegalArgumentException when [value] is malformed or is not a calendar date.
         */
        fun parse(value: String): TemplateVersion {
            val match = pattern.matchEntire(value)
                ?: throw IllegalArgumentException(
                    "expected YYYY-MM-DD or YYYY-MM-DD.N (with N greater than zero)",
                )
            val date = try {
                LocalDate.parse(match.groupValues[1])
            } catch (exception: DateTimeParseException) {
                throw IllegalArgumentException("invalid calendar date", exception)
            }
            val revision = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt() ?: 0
            return TemplateVersion(date, revision)
        }
    }
}

/**
 * Validates the repository-wide contracts that keep UKPT safe to copy and update as a template.
 *
 * Validation covers the template marker, migration filenames and sections, shared agent guidance,
 * canonical skill metadata, and the compatibility links exposed to Claude Code.
 */
object TemplateRepositoryValidator {
    private val migrationName =
        Regex("""^(\d{4}-\d{2}-\d{2}(?:\.[1-9]\d*)?)-[a-z0-9]+(?:-[a-z0-9]+)*\.md$""")
    private val requiredMigrationHeadings = listOf("## Detection", "## Migration", "## Verification")

    /** Returns all validation issues in [repository] so callers can report them together. */
    fun validate(repository: Path): List<TemplateValidationIssue> = buildList {
        val templateVersion = validateMarker(repository, this)
        validateMigrations(repository, templateVersion, this)
        validateAgentGuidance(repository, this)
        validateSkills(repository, this)
    }

    private fun validateMarker(
        repository: Path,
        issues: MutableList<TemplateValidationIssue>,
    ): TemplateVersion? {
        val relativePath = ".ukpt/template.json"
        val marker = repository.resolve(relativePath)
        if (!Files.isRegularFile(marker)) {
            issues += TemplateValidationIssue(relativePath, "missing template marker")
            return null
        }

        val version = try {
            Json.parseToJsonElement(marker.readText())
                .jsonObject["templateVersion"]
                ?.jsonPrimitive
                ?.contentOrNull
        } catch (exception: Exception) {
            issues += TemplateValidationIssue(relativePath, "invalid JSON: ${exception.message}")
            return null
        }
        if (version == null) {
            issues += TemplateValidationIssue(relativePath, "templateVersion must be a string")
            return null
        }
        return try {
            TemplateVersion.parse(version)
        } catch (exception: IllegalArgumentException) {
            issues += TemplateValidationIssue(relativePath, "invalid templateVersion '$version': ${exception.message}")
            null
        }
    }

    private fun validateMigrations(
        repository: Path,
        currentVersion: TemplateVersion?,
        issues: MutableList<TemplateValidationIssue>,
    ) {
        val migrationsDirectory = repository.resolve("docs/template-migrations")
        if (!Files.isDirectory(migrationsDirectory)) {
            issues += TemplateValidationIssue("docs/template-migrations", "missing migrations directory")
            return
        }

        val migrations = Files.list(migrationsDirectory).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.name.endsWith(".md") && it.name != "README.md" }
                .sorted()
                .collect(Collectors.toList())
        }
        migrations.forEach { migration ->
            val relativePath = repository.relativize(migration).toString()
            val match = migrationName.matchEntire(migration.name)
            if (match == null) {
                issues += TemplateValidationIssue(
                    relativePath,
                    "filename must be <templateVersion>-<lowercase-slug>.md",
                )
                return@forEach
            }

            val migrationVersion = try {
                TemplateVersion.parse(match.groupValues[1])
            } catch (exception: IllegalArgumentException) {
                issues += TemplateValidationIssue(relativePath, "invalid version: ${exception.message}")
                return@forEach
            }
            if (currentVersion != null && migrationVersion > currentVersion) {
                issues += TemplateValidationIssue(relativePath, "migration is newer than templateVersion")
            }

            val contents = migration.readText()
            requiredMigrationHeadings.forEach { heading ->
                if (!Regex("(?m)^${Regex.escape(heading)}\\s*$").containsMatchIn(contents)) {
                    issues += TemplateValidationIssue(relativePath, "missing '$heading' section")
                }
            }
        }
    }

    private fun validateAgentGuidance(
        repository: Path,
        issues: MutableList<TemplateValidationIssue>,
    ) {
        val agents = repository.resolve("AGENTS.md")
        if (!Files.isRegularFile(agents)) {
            issues += TemplateValidationIssue("AGENTS.md", "missing project agent guidance")
        } else if (!agents.readText().contains("UKPT.md")) {
            issues += TemplateValidationIssue("AGENTS.md", "must direct agents to UKPT.md")
        }

        val claude = repository.resolve("CLAUDE.md")
        if (!Files.isRegularFile(claude)) {
            issues += TemplateValidationIssue("CLAUDE.md", "missing Claude compatibility guidance")
        } else {
            val imports = claude.readText().lineSequence().map(String::trim).toSet()
            listOf("@AGENTS.md", "@UKPT.md").forEach { requiredImport ->
                if (requiredImport !in imports) {
                    issues += TemplateValidationIssue("CLAUDE.md", "missing '$requiredImport' import")
                }
            }
        }
    }

    private fun validateSkills(
        repository: Path,
        issues: MutableList<TemplateValidationIssue>,
    ) {
        val canonicalRoot = repository.resolve(".agents/skills")
        if (!Files.isDirectory(canonicalRoot)) {
            issues += TemplateValidationIssue(".agents/skills", "missing canonical skill directory")
            return
        }
        val skillDirectories = Files.list(canonicalRoot).use { paths ->
            paths
                .filter { Files.isDirectory(it) && it.name.startsWith("ukpt-") }
                .sorted()
                .collect(Collectors.toList())
        }
        if (skillDirectories.isEmpty()) {
            issues += TemplateValidationIssue(".agents/skills", "no ukpt-* skills found")
        }

        val canonicalNames = skillDirectories.mapTo(mutableSetOf()) { it.name }
        skillDirectories.forEach { skill -> validateSkill(repository, skill, issues) }

        val claudeRoot = repository.resolve(".claude/skills")
        canonicalNames.forEach { name ->
            val relativePath = ".claude/skills/$name"
            val compatibilityLink = repository.resolve(relativePath)
            val expectedTarget = Path.of("../../.agents/skills/$name")
            if (!Files.isSymbolicLink(compatibilityLink)) {
                issues += TemplateValidationIssue(relativePath, "must be a symbolic link to $expectedTarget")
            } else if (Files.readSymbolicLink(compatibilityLink) != expectedTarget) {
                issues += TemplateValidationIssue(
                    relativePath,
                    "points to ${Files.readSymbolicLink(compatibilityLink)} instead of $expectedTarget",
                )
            }
        }

        if (Files.isDirectory(claudeRoot)) {
            Files.list(claudeRoot).use { paths ->
                paths.filter { it.name.startsWith("ukpt-") && it.name !in canonicalNames }.forEach { extra ->
                    issues += TemplateValidationIssue(
                        repository.relativize(extra).toString(),
                        "has no matching canonical skill under .agents/skills",
                    )
                }
            }
        }
    }

    private fun validateSkill(
        repository: Path,
        skill: Path,
        issues: MutableList<TemplateValidationIssue>,
    ) {
        val skillName = skill.name
        val skillFile = skill.resolve("SKILL.md")
        val skillPath = repository.relativize(skillFile).toString()
        if (!Files.isRegularFile(skillFile)) {
            issues += TemplateValidationIssue(skillPath, "missing SKILL.md")
            return
        }

        val contents = skillFile.readText()
        val frontmatter = Regex("""\A---\n(.*?)\n---""", RegexOption.DOT_MATCHES_ALL)
            .find(contents)
            ?.groupValues
            ?.get(1)
        if (frontmatter == null) {
            issues += TemplateValidationIssue(skillPath, "missing YAML frontmatter")
        } else {
            val keys = frontmatter.lineSequence()
                .filter { it.isNotBlank() && !it.first().isWhitespace() && ':' in it }
                .map { it.substringBefore(':') }
                .toSet()
            if (keys != setOf("name", "description")) {
                issues += TemplateValidationIssue(skillPath, "frontmatter keys must be name and description")
            }
            val declaredName = Regex("(?m)^name:\\s*([a-z0-9-]+)\\s*$")
                .find(frontmatter)
                ?.groupValues
                ?.get(1)
            if (declaredName != skillName) {
                issues += TemplateValidationIssue(skillPath, "name '$declaredName' must match directory '$skillName'")
            }
        }

        val metadata = skill.resolve("agents/openai.yaml")
        val metadataPath = repository.relativize(metadata).toString()
        if (!Files.isRegularFile(metadata)) {
            issues += TemplateValidationIssue(metadataPath, "missing Codex UI metadata")
        } else if (!metadata.readText().contains("\$$skillName")) {
            issues += TemplateValidationIssue(metadataPath, "default_prompt must mention \$$skillName")
        }
    }
}
