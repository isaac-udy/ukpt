package architecture

import architecture.registry.Violation
import architecture.registry.renderRuleIndex
import architecture.registry.rules
import architecture.registry.verify
import architecture.rules.UkptArchitecture
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The single entry point for the registry-driven architecture rules: runs every rule in the
 * catalog (constructs + exhaustiveness + scope/module-graph constraints) and fails once with a
 * report grouped by rule id. [ruleIndexMatchesReadme] keeps the README's published rule index in
 * lock-step with the catalog.
 */
class RegistryArchitectureTest {

    @Test
    fun architecture() = verify(UkptArchitecture.all)

    /**
     * Doc↔registry sync: the README's `RULE-INDEX` block must list exactly the rules the catalog
     * enforces (id, enforcement tag, statement), so the documented index can never silently drift
     * from what runs. Regenerate after changing rules with:
     *
     *     UPDATE_RULE_INDEX=true ./gradlew :platform:common:architecture:test
     */
    @Test
    fun ruleIndexMatchesReadme() {
        val readme = readmeFile()
        val text = readme.readText()
        val start = "<!-- RULE-INDEX:START -->"
        val end = "<!-- RULE-INDEX:END -->"
        val startIdx = text.indexOf(start)
        val endIdx = text.indexOf(end)
        require(startIdx >= 0 && endIdx > startIdx) {
            "README ${readme.path} is missing the rule-index markers ($start … $end)"
        }

        val expectedBlock = "$start\n\n${renderRuleIndex(UkptArchitecture.all)}\n\n$end"
        val currentBlock = text.substring(startIdx, endIdx + end.length)

        if (System.getenv("UPDATE_RULE_INDEX") == "true") {
            if (currentBlock != expectedBlock) {
                readme.writeText(text.substring(0, startIdx) + expectedBlock + text.substring(endIdx + end.length))
                println("RULE-INDEX block regenerated in ${readme.path}")
            }
            return
        }

        assertEquals(
            expectedBlock,
            currentBlock,
            "The README rule index is stale relative to the registry. Regenerate it with:\n" +
                "  UPDATE_RULE_INDEX=true ./gradlew :platform:common:architecture:test",
        )
    }

    /** Locate the architecture module's README from the test working directory. */
    private fun readmeFile(): File {
        File("README.md").let { if (it.exists()) return it }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            File(dir, "platform/common/architecture/README.md").let { if (it.exists()) return it }
            dir = dir.parentFile
        }
        error("Could not locate platform/common/architecture/README.md from ${File("").absolutePath}")
    }

    /**
     * Self-check: proves the runner actually detects and reports violations (so a green
     * [architecture] run means "no violations", not "nothing ran"), and that the module-graph
     * provider parses real edges (so the module rules aren't passing vacuously).
     */
    @Test
    fun runnerDetectsViolationsAndParsesGraph() {
        val sentinel by rules {
            @Suppress("unused")
            val alwaysFails by rule("Always reports a violation") {
                scope { _, _ -> listOf(Violation("sentinel", "intentional self-check violation")) }
            }
            @Suppress("unused")
            val graphParses by rule("The module graph parses at least one module edge") {
                moduleGraph { graph, _ ->
                    if (graph.edges.isEmpty()) listOf(Violation("graph", "no module edges were parsed")) else emptyList()
                }
            }
        }

        val error = assertFailsWith<AssertionError> { verify(listOf(sentinel)) }
        val message = error.message.orEmpty()
        assertTrue("sentinel.alwaysFails" in message, "the runner should report the violation by id; was:\n$message")
        assertTrue("sentinel.graphParses" !in message, "the module graph should parse real edges; was:\n$message")
    }
}
