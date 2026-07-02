package architecture

import architecture.registry.ArchitectureRun
import architecture.registry.Describe
import architecture.registry.RuleGroup
import architecture.registry.Violation
import architecture.registry.verify
import architecture.rules.UkptArchitecture
import architecture.testing.assertCatalogSourcesRegistered
import architecture.testing.assertEveryGroupHasATestFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-checks on the architecture-test machinery itself — the actual rules run in
 * [UkptArchitectureTest]; the generated docs are checked by [UkptArchitectureDocsTest].
 */
class RegistryIntegrityTest {

    /**
     * Proves the runner actually detects and reports violations (so a green [UkptArchitectureTest]
     * run means "no violations", not "nothing ran"), and that the module-graph provider parses real
     * edges (so the module rules aren't passing vacuously).
     */
    @Test
    fun runnerDetectsViolationsAndParsesGraph() {
        val error = assertFailsWith<Throwable> {
            verify(ArchitectureRun(listOf(Sentinel), scopeProvider = { projectScope }))
        }
        val message = error.message.orEmpty()
        assertTrue("Sentinel.alwaysFails" in message, "the runner should report the violation by id; was:\n$message")
        assertTrue("Sentinel.graphParses" !in message, "the module graph should parse real edges; was:\n$message")
    }

    @Test
    fun everyDeclaredConstructAndGroupIsRegistered() {
        assertCatalogSourcesRegistered(UkptArchitecture)
    }

    @Test
    fun everyGroupHasATestFactory() {
        assertEveryGroupHasATestFactory(UkptArchitecture, UkptArchitectureTest::class)
    }
}

/** Sentinel group for [RegistryIntegrityTest.runnerDetectsViolationsAndParsesGraph]. */
private object Sentinel : RuleGroup() {
    @Suppress("unused")
    @Describe("Always reports a violation")
    val alwaysFails by rule {
        scope { _, _ -> listOf(Violation("sentinel", "intentional self-check violation")) }
    }

    @Suppress("unused")
    @Describe("The module graph parses at least one module edge")
    val graphParses by rule {
        moduleGraph { graph, _ ->
            if (graph.edges.isEmpty()) listOf(Violation("graph", "no module edges were parsed")) else emptyList()
        }
    }
}
