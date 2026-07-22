package ukpt.template

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class TemplateRepositoryValidatorTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun acceptsValidTemplateMetadataAndSkills() {
        createValidRepository()

        assertEquals(emptyList(), TemplateRepositoryValidator.validate(repository))
    }

    @Test
    fun reportsMarkerMigrationGuidanceAndLinkFailuresTogether() {
        createValidRepository()
        repository.resolve(".ukpt/template.json").writeText("""{"templateVersion":"2026-99-01"}""")
        repository.resolve("docs/template-migrations/2026-07-15.2-example.md").writeText("# Example\n")
        Files.delete(repository.resolve(".claude/skills/ukpt-example"))
        repository.resolve(".claude/skills/ukpt-example").writeText("not a link")
        repository.resolve("CLAUDE.md").writeText("@UKPT.md\n")

        val issues = TemplateRepositoryValidator.validate(repository)

        assertTrue(issues.any { it.path == ".ukpt/template.json" && "invalid calendar date" in it.message })
        assertTrue(issues.any { it.path.endsWith("example.md") && "missing '## Detection'" in it.message })
        assertTrue(issues.any { it.path == "CLAUDE.md" && "@AGENTS.md" in it.message })
        assertTrue(issues.any { it.path == ".claude/skills/ukpt-example" && "symbolic link" in it.message })
    }

    @Test
    fun reportsSkillReferencesThatNoLongerResolve() {
        createValidRepository()
        repository.resolve("platform/common/architecture/docs").createDirectories()
        repository.resolve("platform/common/architecture/docs/rule-index.md").writeText(
            "| `UiLayer.Composable.screenContentPreview` | Statement | [tested](x) |\n",
        )
        repository.resolve("feature/core/client").createDirectories()
        repository.resolve("feature/core/client/Present.kt").writeText("// present\n")
        repository.resolve(".agents/skills/ukpt-example/SKILL.md").writeText(
            """
            ---
            name: ukpt-example
            description: Example UKPT skill used by the validator test.
            ---

            # Example

            Copy `feature/core/client/Present.kt` and `feature/core/client/Gone.kt`.
            Honour `UiLayer.Composable.screenContentPreview` and `UiLayer.Composable.removedRule`.
            Ignore `Modifier.padding`, `<name>/Templated.kt`, `design-system/README.md`,
            `feature/core/{api,client,server}` and [the template](https://github.com/isaac-udy/ukpt).
            See [templates](templates.md) and [missing](nope.md).
            """.trimIndent(),
        )
        repository.resolve(".agents/skills/ukpt-example/templates.md").writeText("# Templates\n")

        val issues = TemplateRepositoryValidator.validate(repository)
        val messages = issues.filter { it.path.endsWith("ukpt-example/SKILL.md") }.map { it.message }

        assertTrue(messages.any { it == "referenced path does not exist: feature/core/client/Gone.kt" })
        assertTrue(messages.any { it == "unknown architecture rule id: UiLayer.Composable.removedRule" })
        assertTrue(messages.any { it == "link target does not exist: nope.md" })
        // Present paths, known rule ids, non-rule dotted names, placeholders, paths that are not
        // repository-rooted, remote links and resolvable links must all stay quiet.
        assertEquals(3, messages.size, "unexpected extra issues: $messages")
    }

    private fun createValidRepository() {
        repository.resolve(".ukpt").createDirectories()
        repository.resolve(".ukpt/template.json").writeText("""{"templateVersion":"2026-07-15.2"}""")

        repository.resolve("docs/template-migrations").createDirectories()
        repository.resolve("docs/template-migrations/2026-07-15.1-example.md").writeText(
            """
            # Example

            ## Detection
            Detection.

            ## Migration
            Migration.

            ## Verification
            Verification.
            """.trimIndent(),
        )

        repository.resolve("AGENTS.md").writeText("Read UKPT.md first.\n")
        repository.resolve("CLAUDE.md").writeText("@AGENTS.md\n@UKPT.md\n")

        val skill = repository.resolve(".agents/skills/ukpt-example")
        skill.resolve("agents").createDirectories()
        skill.resolve("SKILL.md").writeText(
            """
            ---
            name: ukpt-example
            description: Example UKPT skill used by the validator test.
            ---

            # Example
            """.trimIndent(),
        )
        skill.resolve("agents/openai.yaml").writeText(
            """
            interface:
              display_name: "Example"
              short_description: "Example UKPT skill for tests"
              default_prompt: "Use ${'$'}ukpt-example for an example."
            """.trimIndent(),
        )

        val claudeSkills = repository.resolve(".claude/skills").createDirectories()
        Files.createSymbolicLink(
            claudeSkills.resolve("ukpt-example"),
            Path.of("../../.agents/skills/ukpt-example"),
        )
    }
}
