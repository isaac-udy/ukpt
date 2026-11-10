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
 * The Testing rules over `src/architectureTest/fixtures/test-categories`: a `feature.sample` server
 * with two pages, its `:tests` module, and `:app:server` end-to-end tests. Each test below names the
 * fixture files that break its rule; `SamplePageTest`, `SamplePageRobot`, `AddSampleImplTest`,
 * `SampleEventsTest`, `SamplePageSnapshotTest` and `SampleJourneyTest` break none.
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

    private fun List<Violation>.messages() = joinToString("\n") { it.message }

    @Test
    fun `a test class without a category is reported`() {
        assertEquals(listOf("UncategorisedTest.kt"), violations("Testing.oneCategoryPerTestClass").files())
    }

    @Test
    fun `a test-source class matching no test construct is reported, robots, scenarios, test graphs and fakes match theirs`() {
        val violations = violations("architecture.everyTestDeclarationBelongsToATestConstruct")
        assertEquals(listOf("UncategorisedTest.kt", "UncategorisedTest.kt"), violations.files())
    }

    @Test
    fun `a test function without an assertion is reported, one asserting through a helper or a robot is not`() {
        assertEquals(listOf("CombinedUnitTest.kt"), violations("Testing.testFunctionsAssert").files())
    }

    @Test
    fun `a unit test constructing two production classes is reported, View States and Forms are not counted`() {
        val violations = violations("Testing.UnitTest.oneProductionClass")
        assertEquals(listOf("CombinedUnitTest.kt"), violations.files())
        assertTrue("AddSampleImpl" in violations.messages() && "SampleRepository" in violations.messages(), violations.messages())
    }

    @Test
    fun `a unit test importing koin is reported`() {
        assertEquals(listOf("CombinedUnitTest.kt"), violations("Testing.UnitTest.noInfrastructure").files())
    }

    @Test
    fun `a unit test in a tests module is reported`() {
        assertEquals(listOf("MisplacedUnitTest.kt"), violations("Testing.UnitTest.placement").files())
    }

    @Test
    fun `a subcutaneous test outside a tests module is reported`() {
        assertEquals(listOf("MisplacedSubcutaneousTest.kt"), violations("Testing.SubcutaneousTest.placement").files())
    }

    @Test
    fun `a subcutaneous test referencing a Routes class or a repository is reported, one referencing Paths is not`() {
        val violations = violations("Testing.SubcutaneousTest.entryPointVocabularyOnly")
        assertEquals(listOf("ShortcutSubcutaneousTest.kt", "ShortcutSubcutaneousTest.kt"), violations.files())
        assertTrue("SampleRoutes" in violations.messages() && "SampleRepository" in violations.messages(), violations.messages())
    }

    @Test
    fun `a subcutaneous test function without asUser is reported, a call named only in a comment does not count`() {
        val violations = violations("Testing.SubcutaneousTest.runsInTheHarness")
        assertEquals(listOf("ShortcutSubcutaneousTest.kt"), violations.files())
        assertTrue("asUser(" in violations.single().message, violations.single().message)
    }

    @Test
    fun `a subcutaneous test importing jsoup or a ktor client is reported`() {
        val violations = violations("Testing.SubcutaneousTest.noInfrastructureImports")
        assertEquals(List(3) { "ShortcutSubcutaneousTest.kt" }, violations.files())
        assertTrue("org.jsoup.Jsoup" in violations.messages() && "io.ktor.client.request.post" in violations.messages(), violations.messages())
    }

    @Test
    fun `a component test outside a server module is reported`() {
        assertEquals(listOf("MisplacedComponentTest.kt"), violations("Testing.ComponentTest.placement").files())
    }

    @Test
    fun `a component test referencing a Routes class and Ktor's test engine is reported, one rendering an event stream is not`() {
        val violations = violations("Testing.ComponentTest.rendersMarkupOnly")
        assertEquals(listOf("RoutesComponentTest.kt", "RoutesComponentTest.kt"), violations.files())
        assertTrue("SampleRoutes" in violations.messages() && "io.ktor.server.testing" in violations.messages(), violations.messages())
    }

    @Test
    fun `a snapshot test in a tests module is reported`() {
        assertEquals(listOf("MisplacedSnapshotTest.kt"), violations("Testing.SnapshotTest.placement").files())
    }

    @Test
    fun `a snapshot test comparing without HtmlSnapshots is reported`() {
        assertEquals(listOf("HandRolledSnapshotTest.kt"), violations("Testing.SnapshotTest.usesTheSnapshotHarness").files())
    }

    @Test
    fun `an end-to-end test outside app server is reported`() {
        assertEquals(listOf("MisplacedEndToEndTest.kt"), violations("Testing.EndToEndTest.placement").files())
    }

    @Test
    fun `an end-to-end test that opens no browser is reported, a call named only in a comment does not count`() {
        val violations = violations("Testing.EndToEndTest.drivesTheServerInABrowser")
        assertEquals(listOf("RequestOnlyEndToEndTest.kt"), violations.files())
        assertTrue("asUserInBrowser(" in violations.single().message, violations.single().message)
    }

    @Test
    fun `an end-to-end test importing Playwright is reported, one using robots is not`() {
        assertEquals(listOf("PlaywrightEndToEndTest.kt"), violations("Testing.EndToEndTest.drivesThroughRobots").files())
    }

    @Test
    fun `a robot in a tests module's src test is reported`() {
        assertEquals(listOf("MisplacedPageRobot.kt"), violations("Testing.Robot.placement").files())
    }

    @Test
    fun `a robot with no entry point, two entry points or a misnamed one is reported, an entry point named only in a comment does not count`() {
        val violations = violations("Testing.Robot.onePageEntryPoint")
        assertEquals(listOf("MisnamedPageRobot.kt", "NoEntryPageRobot.kt", "TwoEntryPageRobot.kt"), violations.files())
        assertTrue("onMisnamedScreen" in violations.messages() && "onOtherPage" in violations.messages(), violations.messages())
    }

    @Test
    fun `a robot function not named for a user action is reported`() {
        val violations = violations("Testing.Robot.userActionNames")
        assertEquals(listOf("NoEntryPageRobot.kt"), violations.files())
        assertTrue("`save`" in violations.single().message, violations.single().message)
    }

    @Test
    fun `a robot whose entry point no subcutaneous test calls is reported, also when an end-to-end test calls it`() {
        val violations = violations("Testing.Robot.usedBySubcutaneousTests")
        assertEquals(listOf("MisnamedPageRobot.kt", "NoEntryPageRobot.kt", "SummaryPageRobot.kt"), violations.files())
    }

    @Test
    fun `a page with no robot is reported`() {
        val violations = violations("ServerWeb.Page.hasARobot")
        assertEquals(listOf("ArchivePage.kt"), violations.files())
        assertTrue("ArchivePageRobot" in violations.single().message, violations.single().message)
    }

    @Test
    fun `a scenario using a repository, Exposed and SQL is reported, one using domain interfaces is not`() {
        val violations = violations("Testing.Scenario.seedsThroughServerDomain")
        assertEquals(List(3) { "StorageScenarios.kt" }, violations.files())
        val messages = violations.messages()
        assertTrue("SampleRepository" in messages && "SQL" in messages && "org.jetbrains.exposed" in messages, messages)
    }

    @Test
    fun `a tests module with two test graphs is reported`() {
        val violations = violations("Testing.TestGraph.onePerTestsModule")
        assertEquals(1, violations.size, violations.toString())
        assertTrue("2 Test Graphs" in violations.single().message, violations.single().message)
    }

    @Test
    fun `a test graph declaring a module inline and naming a repository is reported, one listing the dependency module is not`() {
        val violations = violations("Testing.TestGraph.productionModulesOnly")
        assertEquals(listOf("InlineTestGraph.kt", "InlineTestGraph.kt"), violations.files())
        assertTrue("inline" in violations.messages() && "SampleRepository" in violations.messages(), violations.messages())
    }

    @Test
    fun `a fake of a domain interface in a tests module is reported, a fake clock is not`() {
        assertEquals(listOf("FakeFlowOfSamples.kt"), violations("Testing.Fake.subcutaneousEdgesOnly").files())
    }

    private fun edge(from: String, to: String, configuration: String = "implementation") =
        ModuleEdge(from = from, to = to, file = "build.gradle.kts", line = 1, exemptRuleIds = emptySet(), configuration = configuration)

    @Test
    fun `a tests module missing its feature's server is reported`() {
        val check = (ModuleRules.testsModuleShape.enforcement as ModuleGraphConstraint).check
        val graph = ModuleGraph(
            listOf(
                edge(":feature:sample:tests", ":feature:sample:server"),
                edge(":feature:other:server", ":feature:other:api"),
                edge(":feature:other:tests", ":platform:common:subcutaneous"),
            ),
        )
        assertEquals(listOf(":feature:other:tests does not depend on :feature:other:server"), check.run(graph) { false }.map { it.message })
    }

    @Test
    fun `a production edge to a test-support module is reported, a test edge or one from another test-support module is not`() {
        val check = (ModuleRules.testSupportOnlyFromTests.enforcement as ModuleGraphConstraint).check
        val graph = ModuleGraph(
            listOf(
                edge(":feature:sample:server", ":platform:common:testing", configuration = "testImplementation"),
                edge(":feature:sample:tests", ":platform:common:subcutaneous"),
                edge(":app:server", ":feature:sample:tests", configuration = "testImplementation"),
                edge(":feature:sample:server", ":platform:common:robots"),
                edge(":app:server", ":platform:common:browser"),
            ),
        )
        assertEquals(
            listOf(":feature:sample:server -> :platform:common:robots", ":app:server -> :platform:common:browser"),
            check.run(graph) { false }.map { it.where.substringBefore(" (") },
        )
    }
}
