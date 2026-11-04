package architecture.rules.testing

import architecture.isTestSource
import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import architecture.rules.module.ModuleRules
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
 * The Testing rules over `src/architectureTest/fixtures/test-categories`: a `feature.sample` client
 * with two ViewModels, a Repository and two HTML ScreenContents, a `:tests` module, and tests that
 * break one rule each, beside `SampleRepositoryTest`, `SampleViewModelTest` and the desktop
 * `SampleJourneyTest`, which break none.
 */
class TestCategoriesTest {

    private val root = File("src/architectureTest/fixtures/test-categories").absolutePath
    private val fixture = Konsist.scopeFromExternalDirectory(root)

    // Relative to the fixture root: the fixture itself sits under `src/architectureTest/`.
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
    fun `a test class without a category is reported`() {
        assertEquals(listOf("UncategorisedTest.kt"), violations("Testing.oneCategoryPerTestClass").files())
    }

    @Test
    fun `a test-source class matching no test construct is reported`() {
        val violations = violations("architecture.everyTestDeclarationBelongsToATestConstruct")
        assertEquals(listOf("UncategorisedTest.kt", "UncategorisedTest.kt"), violations.files())
    }

    @Test
    fun `a test function without an assertion is reported, one asserting through a helper is not`() {
        assertEquals(listOf("CombinedUnitTest.kt"), violations("Testing.testFunctionsAssert").files())
    }

    @Test
    fun `a unit test constructing two production classes is reported, a value type is not counted`() {
        val violations = violations("Testing.UnitTest.oneProductionClass")
        assertEquals(listOf("CombinedUnitTest.kt"), violations.files())
        assertTrue("SampleRepository" in violations.single().message && "SampleViewModel" in violations.single().message)
    }

    @Test
    fun `a unit test importing koin is reported`() {
        assertEquals(listOf("CombinedUnitTest.kt"), violations("Testing.UnitTest.noInfrastructure").files())
    }

    @Test
    fun `a unit test in androidHostTest is reported`() {
        assertEquals(listOf("MisplacedUnitTest.kt"), violations("Testing.UnitTest.placement").files())
    }

    @Test
    fun `a component test referencing a ViewModel is reported`() {
        val violations = violations("Testing.ComponentTest.rendersScreenContentOnly")
        assertEquals(listOf("SampleHtmlScreenContentTest.kt"), violations.files())
        assertTrue("SampleViewModel" in violations.single().message, violations.single().message)
    }

    @Test
    fun `a subcutaneous test referencing a repository is reported, one using entry-point vocabulary is not`() {
        val violations = violations("Testing.SubcutaneousTest.entryPointVocabularyOnly")
        assertEquals(listOf("ShortcutSubcutaneousTest.kt"), violations.files())
        assertTrue("SampleRepository" in violations.single().message, violations.single().message)
    }

    @Test
    fun `a subcutaneous test constructing its ViewModel is reported`() {
        val violations = violations("Testing.SubcutaneousTest.viewModelsFromTheGraph")
        assertEquals(listOf("ShortcutSubcutaneousTest.kt"), violations.files())
        assertTrue("SampleViewModel" in violations.single().message)
    }

    @Test
    fun `a subcutaneous test function outside the harness is reported, a call named only in a comment does not count`() {
        assertEquals(listOf("ShortcutSubcutaneousTest.kt"), violations("Testing.SubcutaneousTest.runsInTheHarness").files())
    }

    @Test
    fun `a subcutaneous test importing koin is reported`() {
        assertEquals(listOf("ShortcutSubcutaneousTest.kt"), violations("Testing.SubcutaneousTest.noInfrastructureImports").files())
    }

    @Test
    fun `an end-to-end test that renders one screen is reported, one hosting the app root is not`() {
        val violations = violations("Testing.EndToEndTest.drivesTheAppRoot")
        assertEquals(listOf("ScreenOnlyEndToEndTest.kt", "ScreenOnlyEndToEndTest.kt"), violations.files())
        assertTrue(violations.any { "inProcessServer(" in it.message } && violations.any { "AppContent(" in it.message }, violations.toString())
    }

    @Test
    fun `a scenario using Exposed and SQL is reported, one using a domain interface is not`() {
        val violations = violations("Testing.Scenario.seedsThroughServerDomain")
        assertEquals(listOf("StorageScenarios.kt", "StorageScenarios.kt"), violations.files())
        assertTrue(violations.any { "SQL" in it.message } && violations.any { "org.jetbrains.exposed" in it.message })
    }

    @Test
    fun `scenarios and test graphs are not fixtures`() {
        assertTrue(violations("architecture.everyTestDeclarationBelongsToATestConstruct").none { "Scenarios" in it.where || "TestGraph" in it.where })
    }

    @Test
    fun `a tests module with two test graphs is reported`() {
        val violations = violations("Testing.TestGraph.onePerTestsModule")
        assertEquals(1, violations.size, violations.toString())
        assertTrue("2 Test Graphs" in violations.single().message)
    }

    @Test
    fun `a test graph declaring a module inline is reported, one listing dependency modules is not`() {
        assertEquals(listOf("InlineTestGraph.kt"), violations("Testing.TestGraph.productionModulesOnly").files())
    }

    @Test
    fun `a fake of a domain interface in a tests module is reported, a fake clock is not`() {
        assertEquals(listOf("FakeSampleSource.kt"), violations("Testing.Fake.subcutaneousEdgesOnly").files())
    }

    @Test
    fun `a ViewModel no subcutaneous test references is reported`() {
        val violations = violations("ClientUi.ViewModel.subcutaneouslyTested")
        assertEquals(listOf("UntestedViewModel.kt"), violations.files())
    }

    @Test
    fun `a ViewModel function no subcutaneous test calls is reported, a name in a comment does not count`() {
        val violations = violations("ClientUi.ViewModel.publicFunctionsExercised")
        assertEquals(listOf("SampleViewModel.kt"), violations.files())
        assertTrue("onDelete" in violations.single().message)
    }

    @Test
    fun `a tests module missing its feature's server is reported`() {
        val check = (ModuleRules.testsModuleShape.enforcement as ModuleGraphConstraint).check
        val graph = ModuleGraph(
            listOf(
                edge(":feature:sample:tests", ":feature:sample:client"),
                edge(":feature:sample:server", ":feature:sample:api"),
                edge(":feature:other:tests", ":feature:other:client"),
                edge(":feature:other:tests", ":feature:sample:tests"),
            ),
        )
        assertEquals(listOf(":feature:sample:tests does not depend on :feature:sample:server"), check.run(graph) { false }.map { it.message })
    }

    private fun edge(from: String, to: String) = ModuleEdge(from = from, to = to, file = "build.gradle.kts", line = 1, exemptRuleIds = emptySet())

    @Test
    fun `an HTML ScreenContent named only in a test comment is reported as untested`() {
        val violations = violations("ClientUi.Composable.htmlScreenContentTested")
        assertEquals(listOf("SampleHtmlScreen.kt"), violations.files())
        assertTrue("UntestedHtmlScreenContent" in violations.single().message)
    }
}
