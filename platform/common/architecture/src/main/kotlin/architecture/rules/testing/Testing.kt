package architecture.rules.testing

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    Every test class declares one category with an annotation from `:platform:common:testing`. The
    category states what the test may use besides the code under test, which decides how much a
    run costs and what a failure means.

    | Category | Exercises | Uses for real | Runs on |
    | --- | --- | --- | --- |
    | `@SubcutaneousTest` | pages, driven through [Robots](#robot), from the production DI graph | everything below the browser: the server in-process, an embedded database, htmx's requests and swaps | JVM |
    | `@UnitTest` | one production class over many inputs | nothing; collaborators are test fakes or lambdas | JVM |
    | `@ComponentTest` | one page, component or event stream, with fixed state | kotlinx.html | JVM |
    | `@SnapshotTest` | pages, compared with golden HTML | kotlinx.html | JVM |
    | `@WiringTest` | a DI graph | the DI container | JVM |
    | `@AdapterTest` | one infrastructure adapter | the infrastructure | JVM |
    | `@CheckTest` | two committed artifacts | files | JVM |
    | `@EndToEndTest` | the server through a browser | the whole application, and Chromium | JVM |

    Subcutaneous tests are the main suite: most behaviour is covered by using a page as a user
    does and reading what it shows. A unit test is for logic with many inputs. A test that needs
    another kind of coverage states it with its category.

    Each annotation carries a JUnit 5 tag and timeout. `./gradlew unitTest` runs the unit tests of
    every module; each category has its task (`subcutaneousTest`, `componentTest`, `snapshotTest`,
    `wiringTest`, `adapterTest`, `checkTest`, `endToEndTest`).

    Subcutaneous Tests and End To End Tests drive pages through [Robots](#robot), one per page,
    named for what the user does: `clickGreetButton()`, `seeGreetings("Hello, Ada")`. The same
    robot runs in both.

    Test-support declarations (fakes, fixtures, scenarios, test graphs, robots) are Constructs too:
    every top-level declaration in test sources matches exactly one.
""")
object Testing : RuleGroup(
    constructs = listOf(
        SubcutaneousTest,
        UnitTest,
        ComponentTest,
        SnapshotTest,
        WiringTest,
        AdapterTest,
        CheckTest,
        EndToEndTest,
        Fake,
        Scenario,
        TestGraph,
        Robot,
        Fixture,
    ),
    codeScope = CodeScope.Test,
) {
    @Describe("Every test class (a class declaring a `@Test` function) must carry exactly one test category annotation")
    val oneCategoryPerTestClass by rule {
        rationale("The category decides which task runs the test and which rules check it.")
        scope { scope, exempt ->
            scope.classes()
                .filter { test -> test.functions().any { it.hasAnnotationWithName("Test") } }
                .filterNot { exempt(it) }
                .mapNotNull { test ->
                    val categories = CATEGORY_ANNOTATIONS.filter { test.hasAnnotationWithName(it) }
                    when (categories.size) {
                        1 -> null
                        0 -> Violation(test, "carries no category annotation")
                        else -> Violation(test, "carries ${categories.joinToString { "@$it" }}")
                    }
                }
        }
    }

    @Describe("Every `@Test` function must assert: call an `assert*`, `fail`, `verify`, `expect*` or `await*` function, or a Robot's `see*` function, directly or through a function declared in the same file")
    val testFunctionsAssert by rule {
        rationale(
            """
            A test without an assertion passes whatever the code does. It reports coverage that
            does not exist.
            """.trimIndent(),
        )
        note("Detection is by name in the source text, so an assertion helper declared in another file needs a name that matches.")
        scope { scope, exempt ->
            scope.files.flatMap { file ->
                val functions = file.functions(includeNested = true, includeLocal = true)
                val asserting = functions.filter { ASSERTION.containsMatchIn(it.text) }.map { it.name }.toSet()
                functions
                    .filter { it.hasAnnotationWithName("Test") }
                    .filterNot { exempt(it) }
                    .filterNot { test -> test.asserts(asserting) }
                    .map { Violation(it, "`${it.name}` asserts nothing") }
            }
        }
    }
}

private val CATEGORY_ANNOTATIONS = listOf(
    "UnitTest",
    "SubcutaneousTest",
    "ComponentTest",
    "SnapshotTest",
    "WiringTest",
    "AdapterTest",
    "CheckTest",
    "EndToEndTest",
)

private val ASSERTION = Regex("""\b(assert\w*|fail|verify|expect\w*|await\w*|see[A-Z]\w*)\s*[(<{]""")

private fun KoFunctionDeclaration.asserts(assertingHelpers: Set<String>): Boolean =
    ASSERTION.containsMatchIn(text) || assertingHelpers.any { helper -> helper != name && Regex("""\b$helper\s*\(""").containsMatchIn(text) }
