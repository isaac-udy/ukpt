package ukpt.template

import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.stream.Collectors
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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
    private val markdownLink = Regex("""\[[^\]]*]\(([^)\s]+)\)""")
    private val backtickQuoted = Regex("""`([^`\n]+)`""")
    private val ruleId = Regex("""`([A-Z][A-Za-z0-9]*(?:\.[A-Za-z][A-Za-z0-9]*)+)`""")
    private val gitSha = Regex("^[0-9a-fA-F]{40}$")

    private val refName = Regex("""^[A-Za-z0-9][A-Za-z0-9._/-]*$""")

    /**
     * Returns all validation issues in [repository] so callers can report them together.
     * [trackedFiles] (repository-relative, as `git ls-files` prints them) enables the flavour
     * manifest's dropped-path check; it is skipped when null.
     */
    fun validate(repository: Path, trackedFiles: List<String>? = null): List<TemplateValidationIssue> = buildList {
        val templateVersion = validateMarker(repository, this)
        validateFlavourManifest(repository, trackedFiles, this)
        validateMigrations(repository, templateVersion, this)
        validateAgentGuidance(repository, this)
        validateSkills(repository, this)
        validateSkillReferences(repository, repositoryIsDownstream(repository), this)
    }

    /**
     * A downstream project's marker carries a `project` rename map (see the `ukpt-new-project`
     * skill); the template's own marker has only a `templateVersion`. This is what distinguishes a
     * repository generated *from* the template from the template repository itself, and lets the
     * template-repo-specific checks be scoped out of a downstream run.
     */
    private fun repositoryIsDownstream(repository: Path): Boolean {
        val marker = repository.resolve(".ukpt/template.json")
        if (!Files.isRegularFile(marker)) return false
        return runCatching {
            Json.parseToJsonElement(marker.readText()).jsonObject["project"] != null
        }.getOrDefault(false)
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

        val root = try {
            Json.parseToJsonElement(marker.readText()).jsonObject
        } catch (exception: IOException) {
            issues += TemplateValidationIssue(relativePath, "unreadable: ${exception.message}")
            return null
        } catch (exception: SerializationException) {
            issues += TemplateValidationIssue(relativePath, "invalid JSON: ${exception.message}")
            return null
        } catch (exception: IllegalArgumentException) {
            issues += TemplateValidationIssue(relativePath, "invalid JSON: ${exception.message}")
            return null
        }

        // A downstream marker (one carrying a `project` rename map) must also carry the rest of the
        // schema the ukpt-template-update skill relies on; the template's own marker is exempt.
        if (root["project"] != null) validateDownstreamMarker(root, submodulePaths(repository), relativePath, issues)

        val branch = root["templateBranch"]
        when {
            branch == null -> Unit
            branch !is JsonPrimitive || !branch.isString ->
                issues += TemplateValidationIssue(relativePath, "templateBranch must be a string")
            !refName.matches(branch.content) ->
                issues += TemplateValidationIssue(relativePath, "templateBranch '${branch.content}' is not a branch name")
        }

        val version = (root["templateVersion"] as? JsonPrimitive)?.contentOrNull
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

    /**
     * Validates the fields a downstream marker must carry beyond `templateVersion` (see the
     * `ukpt-new-project` skill): the `templateCommit` SHA, the `project` rename map
     * (`package`/`name`/`typePrefix`), and a `submodules` SHA for each path in `.gitmodules`. The
     * update skill depends on every one to resolve its base commit and translate template diffs into
     * the project's names, so a marker missing or malforming any of them would pass silently and
     * then misdirect the next update — a missing `templateCommit` falls back to a fuzzy
     * `git log -S`, a missing rename map is worse.
     */
    private fun validateDownstreamMarker(
        marker: JsonObject,
        submodulePaths: List<String>,
        relativePath: String,
        issues: MutableList<TemplateValidationIssue>,
    ) {
        fun sha(value: JsonElement?, field: String) {
            when {
                value == null -> issues += TemplateValidationIssue(relativePath, "downstream marker is missing `$field`")
                value !is JsonPrimitive || !value.isString ->
                    issues += TemplateValidationIssue(relativePath, "`$field` must be a string")
                !gitSha.matches(value.content) ->
                    issues += TemplateValidationIssue(relativePath, "`$field` must be a 40-character git SHA")
            }
        }
        fun nonBlank(value: JsonElement?, field: String) {
            when {
                value == null -> issues += TemplateValidationIssue(relativePath, "downstream marker is missing `$field`")
                value !is JsonPrimitive || !value.isString ->
                    issues += TemplateValidationIssue(relativePath, "`$field` must be a string")
                value.content.isBlank() ->
                    issues += TemplateValidationIssue(relativePath, "`$field` must not be blank")
            }
        }

        sha(marker["templateCommit"], "templateCommit")

        when (val project = marker["project"]) {
            is JsonObject -> listOf("package", "name", "typePrefix").forEach { nonBlank(project[it], "project.$it") }
            else -> issues += TemplateValidationIssue(relativePath, "`project` must be an object with package, name and typePrefix")
        }

        when (val submodules = marker["submodules"]) {
            is JsonObject -> {
                submodulePaths.forEach { sha(submodules[it], "submodules.$it") }
                (submodules.keys - submodulePaths.toSet()).forEach {
                    issues += TemplateValidationIssue(relativePath, "`submodules.$it` is not a submodule path in .gitmodules")
                }
            }
            else -> issues += TemplateValidationIssue(relativePath, "`submodules` must be an object with a SHA for each .gitmodules path")
        }
    }

    private fun submodulePaths(repository: Path): List<String> {
        val gitmodules = repository.resolve(".gitmodules")
        if (!Files.isRegularFile(gitmodules)) return emptyList()
        return Regex("""(?m)^\s*path\s*=\s*(\S+)\s*$""").findAll(gitmodules.readText()).map { it.groupValues[1] }.toList()
    }

    /**
     * A template flavour — a long-lived branch that drops part of the template — declares what it
     * dropped and how each divergent file is merged in `.ukpt/flavour.json`. The checks keep that
     * manifest true: nothing it drops is tracked, the files it names exist, and it names the branch
     * the marker records.
     */
    private fun validateFlavourManifest(
        repository: Path,
        trackedFiles: List<String>?,
        issues: MutableList<TemplateValidationIssue>,
    ) {
        val relativePath = ".ukpt/flavour.json"
        val file = repository.resolve(relativePath)
        if (!Files.isRegularFile(file)) return
        val manifest = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrElse {
            issues += TemplateValidationIssue(relativePath, "invalid JSON: ${it.message}")
            return
        }
        fun issue(message: String) {
            issues += TemplateValidationIssue(relativePath, message)
        }
        fun strings(field: String): List<String> = when (val value = manifest[field]) {
            null -> emptyList()
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
                .also { if (it.size != value.size) issue("`$field` must be an array of strings") }
            else -> emptyList<String>().also { issue("`$field` must be an array of strings") }
        }
        fun notes(value: JsonElement?, field: String): Map<String, String> = when (value) {
            null -> emptyMap()
            is JsonObject -> value.mapValues { (_, note) -> (note as? JsonPrimitive)?.contentOrNull.orEmpty() }
                .also { notes -> notes.filterValues(String::isBlank).keys.forEach { issue("`$field.$it` needs a note") } }
            else -> emptyMap<String, String>().also { issue("`$field` must be an object of path to note") }
        }

        val flavour = (manifest["flavour"] as? JsonPrimitive)?.contentOrNull
        val branch = runCatching {
            (Json.parseToJsonElement(repository.resolve(".ukpt/template.json").readText()).jsonObject["templateBranch"] as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        when {
            flavour.isNullOrBlank() -> issue("`flavour` must name the template branch")
            flavour != branch -> issue("`flavour` '$flavour' must match templateBranch '$branch' in .ukpt/template.json")
        }

        val dropped = strings("dropped")
        val named = notes(manifest["replaced"], "replaced") + notes(manifest["diverged"], "diverged")
        named.keys.filterNot(::isGlob).forEach { path ->
            if (!Files.exists(repository.resolve(path))) issue("`$path` is listed as replaced or diverged but does not exist")
        }

        when (val generated = manifest["generated"]) {
            null -> Unit
            is JsonObject -> if ((generated["command"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) {
                issue("`generated.command` must be the command that regenerates `generated.paths`")
            }
            else -> issue("`generated` must be an object with paths and command")
        }

        val migrations = manifest["migrations"] as? JsonObject
        notes(migrations?.get("skipped"), "migrations.skipped").keys.forEach { name ->
            if (Files.exists(repository.resolve("docs/template-migrations/$name"))) {
                issue("migration `$name` is listed as skipped but still exists")
            }
        }
        notes(migrations?.get("adapted"), "migrations.adapted").keys.forEach { name ->
            if (!Files.exists(repository.resolve("docs/template-migrations/$name"))) {
                issue("migration `$name` is listed as adapted but does not exist")
            }
        }

        if (trackedFiles != null) {
            val matchers = dropped.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
            trackedFiles.filter { tracked -> matchers.any { it.matches(Path.of(tracked)) } }.forEach { tracked ->
                issues += TemplateValidationIssue(tracked, "is tracked but matches a `dropped` pattern in $relativePath")
            }
        }
    }

    private fun isGlob(path: String): Boolean = path.any { it == '*' || it == '?' || it == '{' || it == '[' }

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

    /**
     * Checks that the file paths and architecture rule ids a skill cites still resolve.
     *
     * Skills describe code they do not contain, and nothing compiles that relationship — so when
     * code moves, a skill silently starts instructing agents to copy files that no longer exist.
     * This is the mechanical half of that problem: it cannot tell whether prose is still *true*,
     * only whether the things it names are still *there*, which is where the rot has shown up.
     *
     * The **file-path** checks only make sense in the template repository: a [downstream] project
     * legitimately omits surfaces the template ships (no server, no web, a renamed package), so a
     * skill path like `app/client/web/build.gradle.kts` is *expected* to be absent there and must
     * not fail the build. Those two checks are therefore skipped downstream. The **rule-id** check
     * still runs everywhere — it resolves against the project's own generated rule index (and
     * self-disables when that index is absent), so it stays meaningful after an update.
     */
    private fun validateSkillReferences(
        repository: Path,
        downstream: Boolean,
        issues: MutableList<TemplateValidationIssue>,
    ) {
        val skillsRoot = repository.resolve(".agents/skills")
        if (!Files.isDirectory(skillsRoot)) return // already reported by validateSkills

        val ruleIds = architectureRuleIds(repository)
        // Only ids whose group is a real rule group are checked, so ordinary dotted expressions
        // (`Modifier.padding`, `UkptTheme.colors`) are ignored without an allow-list to maintain.
        // Retired groups are listed explicitly: without them, deleting or renaming a whole group —
        // the highest-blast-radius rule change there is — would silently exempt every skill that
        // still cites it. A migration entry that renames a group adds the old name here.
        val ruleGroups = ruleIds.mapTo(mutableSetOf()) { it.substringBefore('.') } + retiredRuleGroups
        val repositoryRoots = Files.list(repository).use { paths ->
            paths.map { it.name }.collect(Collectors.toSet())
        }

        val pages = Files.walk(skillsRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.name.endsWith(".md") }
                .sorted()
                .collect(Collectors.toList())
        }

        pages.forEach { page ->
            val relativePath = repository.relativize(page).toString()
            val contents = page.readText()

            if (!downstream) {
                markdownLink.findAll(contents)
                    .map { it.groupValues[1].substringBefore('#') }
                    .filter { it.isNotEmpty() && !isRemoteOrTemplated(it) }
                    .distinct()
                    .forEach { link ->
                        if (!Files.exists(page.parent.resolve(link).normalize())) {
                            issues += TemplateValidationIssue(relativePath, "link target does not exist: $link")
                        }
                    }
            }

            val quoted = backtickQuoted.findAll(contents).map { it.groupValues[1].trim() }.distinct().toList()

            // A repository-rooted path: its first segment names something at the repository root.
            // Paths relative to some other module (`design-system/README.md`) are ambiguous from
            // here and are left alone rather than guessed at.
            if (!downstream) {
                quoted
                    .filter { '/' in it && !isRemoteOrTemplated(it) }
                    .filter { it.substringBefore('/') in repositoryRoots }
                    .forEach { path ->
                        if (!Files.exists(repository.resolve(path))) {
                            issues += TemplateValidationIssue(relativePath, "referenced path does not exist: $path")
                        }
                    }
            }

            quoted
                .filter { '.' in it && !isRemoteOrTemplated(it) }
                .filter { it.substringBefore('.') in ruleGroups }
                .forEach { id ->
                    if (id !in ruleIds) {
                        issues += TemplateValidationIssue(relativePath, "unknown architecture rule id: $id")
                    }
                }
        }
    }

    /**
     * Rule groups that no longer exist. A skill citation headed by one of these is always stale —
     * the id can't resolve against any current rule index — so it is reported rather than skipped.
     * Grows when a migration renames or deletes a group; never shrinks.
     */
    private val retiredRuleGroups = setOf(
        "DomainLayer",
        "UiLayer",
        "DataLayer",
        "ServicesLayer",
        "FeatureRootRules",
    )

    /**
     * Every dotted, PascalCase-headed identifier named in the generated rule index — rule ids and
     * the construct ids skills also cite. Empty when the index is absent, which disables the rule-id
     * check rather than reporting a project that has restructured its architecture module.
     */
    private fun architectureRuleIds(repository: Path): Set<String> {
        val index = repository.resolve("platform/common/architecture/docs/rule-index.md")
        if (!Files.isRegularFile(index)) return emptySet()
        return ruleId.findAll(index.readText()).map { it.groupValues[1] }.toSet()
    }

    /**
     * Remote targets, and anything standing in for more than one concrete path: a `<name>`
     * placeholder, an elided `.../` segment, a glob, or brace expansion such as
     * `feature/core/{api,client,server}` — all of which are legitimate prose, and none of which
     * name a file that could exist.
     */
    private fun isRemoteOrTemplated(value: String): Boolean =
        value.startsWith("http://") ||
            value.startsWith("https://") ||
            value.contains("...") ||
            value.any { it == '<' || it == '>' || it == '*' || it == '$' || it == '{' || it == '}' }
}
