package architecture

import architecture.registry.Describe
import architecture.registry.RuleGroup
import architecture.registry.Violation
import architecture.registry.verify
import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.TestFactory
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-checks on the architecture-test machinery itself — the actual rules run in
 * [UkptArchitectureTest]; the generated docs are checked by [ArchitectureDocsTest].
 */
class RegistryIntegrityTest {

    /**
     * Proves the runner actually detects and reports violations (so a green [UkptArchitectureTest]
     * run means "no violations", not "nothing ran"), and that the module-graph provider parses real
     * edges (so the module rules aren't passing vacuously).
     */
    @Test
    fun runnerDetectsViolationsAndParsesGraph() {
        val error = assertFailsWith<Throwable> { verify(listOf(Sentinel)) }
        val message = error.message.orEmpty()
        assertTrue("Sentinel.alwaysFails" in message, "the runner should report the violation by id; was:\n$message")
        assertTrue("Sentinel.graphParses" !in message, "the module graph should parse real edges; was:\n$message")
    }

    /**
     * Meta-rule: the catalog checks itself. `Construct<Group>` declarations and each group's
     * `constructs` list are deliberately redundant — this scan of the module's own sources fails
     * when a declared construct isn't listed (it would be silently unenforced) or a declared
     * group isn't in [UkptArchitecture.all].
     */
    @Test
    fun everyDeclaredConstructAndGroupIsRegistered() {
        // Konsist resolves relative to the repo root, not the test working directory.
        val scope = Konsist.scopeFromDirectory("platform/common/architecture/src/test/kotlin/architecture/rules")
        val declaredConstructs = scope.objects()
            .filter { obj -> obj.parents().any { it.name.substringBefore('<') == "Construct" } }
            .map { "${it.packagee?.name}.${it.name}" }
            .toSet()
        val registeredConstructs = UkptArchitecture.all
            .flatMap { group -> group.constructs.map { "${it.javaClass.packageName}.${it.javaClass.simpleName}" } }
            .toSet()
        assertEquals(
            declaredConstructs,
            registeredConstructs,
            "every top-level Construct object under rules/ must be listed in its RuleGroup's `constructs`",
        )

        val declaredGroups = scope.objects()
            .filter { obj -> obj.parents().any { it.name.substringBefore('<') == "RuleGroup" } }
            .map { it.name }
            .toSet()
        assertEquals(
            declaredGroups,
            UkptArchitecture.all.map { it.id }.toSet(),
            "every RuleGroup object under rules/ must be listed in UkptArchitecture.all",
        )
    }

    /**
     * [UkptArchitectureTest] declares one `@TestFactory` per group so the groups hang directly off
     * the test class — which means a new group needs a new factory. This fails when one is missing,
     * so a group can't be registered in the catalog yet silently never run.
     */
    @Test
    fun everyGroupHasATestFactory() {
        val factoryNames = UkptArchitectureTest::class.declaredFunctions
            .filter { it.findAnnotation<TestFactory>() != null }
            .map { it.name }
            .toSet()
        val missing = UkptArchitecture.all
            .map { it.id.replaceFirstChar(Char::lowercase) }
            .filterNot { it in factoryNames }
        assertTrue(
            missing.isEmpty(),
            "UkptArchitectureTest needs a @TestFactory for: $missing (name it after the group, lowercase first letter)",
        )
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
