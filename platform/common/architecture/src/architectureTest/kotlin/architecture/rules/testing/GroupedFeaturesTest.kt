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
 * `:feature:capture:insta`, a client with an Android Adapter Test, and a client-only `:tests` module.
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
    fun `a client-only test graph in a grouped tests module is the module's one graph`() {
        assertEquals(emptyList(), violations("Testing.TestGraph.onePerTestsModule").files())
        assertEquals(emptyList(), violations("Testing.TestGraph.productionModulesOnly").files())
    }

    @Test
    fun `an adapter test in a grouped client's androidHostTest is placed, one in commonTest is not`() {
        assertEquals(listOf("CommonAdapterTest.kt"), violations("Testing.AdapterTest.placement").files())
    }

    @Test
    fun `a grouped tests module without a server needs only its client`() {
        val check = (ModuleRules.testsModuleShape.enforcement as ModuleGraphConstraint).check
        val graph = ModuleGraph(
            listOf(
                edge(":feature:capture:insta:tests", ":feature:capture:insta:client"),
                edge(":feature:capture:other:tests", ":feature:capture:insta:tests"),
                edge(":feature:capture:other:client", ":feature:capture:insta:api"),
            ),
        )
        assertEquals(
            listOf(":feature:capture:other:tests does not depend on :feature:capture:other:client"),
            check.run(graph) { false }.map { it.message },
        )
    }

    @Test
    fun `a grouped tests module is test support`() {
        val check = (ModuleRules.testSupportOnlyFromTests.enforcement as ModuleGraphConstraint).check
        val graph = ModuleGraph(
            listOf(
                edge(":feature:capture:insta:client", ":feature:capture:insta:tests", sourceSet = "jvmTest"),
                edge(":app:client:common", ":feature:capture:insta:tests"),
            ),
        )
        assertEquals(1, check.run(graph) { false }.size)
    }

    private fun edge(from: String, to: String, sourceSet: String? = null) =
        ModuleEdge(from = from, to = to, file = "build.gradle.kts", line = 1, exemptRuleIds = emptySet(), sourceSet = sourceSet)
}
