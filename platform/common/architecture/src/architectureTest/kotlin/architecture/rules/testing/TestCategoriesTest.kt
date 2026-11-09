package architecture.rules.testing

import architecture.isTestSource
import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.Violation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Testing rules over `src/architectureTest/fixtures/test-categories`: a `feature.sample` client
 * with a ViewModel, a Repository and two HTML ScreenContents, and tests that break one rule each,
 * beside `SampleRepositoryTest`, which breaks none.
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
    fun `an HTML ScreenContent named only in a test comment is reported as untested`() {
        val violations = violations("ClientUi.Composable.htmlScreenContentTested")
        assertEquals(listOf("SampleHtmlScreen.kt"), violations.files())
        assertTrue("UntestedHtmlScreenContent" in violations.single().message)
    }
}
