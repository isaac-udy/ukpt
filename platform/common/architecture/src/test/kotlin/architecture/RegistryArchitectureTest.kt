package architecture

import architecture.registry.Violation
import architecture.registry.rules
import architecture.registry.verify
import architecture.rules.UkptArchitecture
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The single entry point for the registry-driven architecture rules: runs every rule in the
 * catalog (constructs + exhaustiveness + scope/module-graph constraints) and fails once with a
 * report grouped by rule id.
 *
 * During the 2a migration this runs *alongside* the legacy per-rule tests as a cross-check; once
 * the catalog reproduces them, the legacy tests are deleted (sub-commit "cut over").
 */
class RegistryArchitectureTest {

    @Test
    fun architecture() = verify(UkptArchitecture.all)

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
