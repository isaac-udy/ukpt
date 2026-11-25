package architecture.rules.testing

import architecture.isTestSource
import architecture.rules.UkptArchitecture
import architecture.rules.module.ModuleRules
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.ModuleEdge
import dev.isaacudy.udytils.architecture.ModuleGraph
import dev.isaacudy.udytils.architecture.ModuleGraphConstraint
import dev.isaacudy.udytils.architecture.Violation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rules over `src/architectureTest/fixtures/grouped-features`: the grouped feature
 * `:feature:capture:insta`, its server's Adapter Test, and its `:tests` module.
 */
class GroupedFeaturesTest {

    private val root = File("src/architectureTest/fixtures/grouped-features").absolutePath
    private val fixture = Konsist.scopeFromExternalDirectory(root)

    private fun isFixtureTest(path: String) = isTestSource(path.removePrefix(root))

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { fixture.slice { !isFixtureTest(it.path) } },
        membership = UkptArchitecture.membership,
        testScopeProvider = { fixture.slice { isFixtureTest(it.path) } },
        testMembership = UkptArchitecture.testMembership,
    )

    private fun violations(ruleId: String): List<Violation> =
        run.violations(run.rules.first { it.id == ruleId })

    private fun List<Violation>.files() = map { it.where.substringAfterLast('/').substringBefore(':') }.sorted()

    @Test
    fun `a grouped feature's tests module is test source`() {
        assertTrue(isTestSource("/feature/capture/insta/tests/src/main/kotlin/feature/capture/insta/tests/InstaTestGraph.kt"))
    }

    @Test
    fun `a grouped feature declares its packages under its whole name`() {
        assertEquals(emptyList(), violations("ModuleRules.sidePackageMatchesModule").files())
    }

    @Test
    fun `a test graph in a grouped tests module is the module's one graph`() {
        assertEquals(emptyList(), violations("Testing.TestGraph.onePerTestsModule").files())
        assertEquals(emptyList(), violations("Testing.TestGraph.productionModulesOnly").files())
    }

    @Test
    fun `an adapter test in a grouped server is placed, one in the tests module is not`() {
        assertEquals(listOf("MisplacedAdapterTest.kt"), violations("Testing.AdapterTest.placement").files())
    }

    @Test
    fun `a grouped tests module depends on its server`() {
        val check = (ModuleRules.testsModuleShape.enforcement as ModuleGraphConstraint).check
        val graph = ModuleGraph(
            listOf(
                edge(":feature:capture:insta:tests", ":feature:capture:insta:server"),
                edge(":feature:capture:other:tests", ":feature:capture:insta:tests"),
                edge(":feature:capture:other:server", ":feature:capture:insta:api"),
            ),
        )
        assertEquals(
            listOf(":feature:capture:other:tests does not depend on :feature:capture:other:server"),
            check.run(graph) { false }.map { it.message },
        )
    }

    @Test
    fun `a grouped tests module is test support`() {
        val check = (ModuleRules.testSupportOnlyFromTests.enforcement as ModuleGraphConstraint).check
        val graph = ModuleGraph(
            listOf(
                edge(":feature:capture:insta:server", ":feature:capture:insta:tests", sourceSet = "test"),
                edge(":app:server", ":feature:capture:insta:tests"),
            ),
        )
        assertEquals(1, check.run(graph) { false }.size)
    }

    private fun edge(from: String, to: String, sourceSet: String? = null) =
        ModuleEdge(from = from, to = to, file = "build.gradle.kts", line = 1, exemptRuleIds = emptySet(), sourceSet = sourceSet)
}
