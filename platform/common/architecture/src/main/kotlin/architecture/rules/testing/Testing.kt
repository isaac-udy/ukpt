package architecture.rules.testing

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    Every test class declares one category with an annotation from `:platform:common:testing`. The
    category states what the test may use besides the code under test, which decides how long a
    run takes and what a failure means.

    | Category | Exercises | Uses for real | Runs on |
    | --- | --- | --- | --- |
    | `@SubcutaneousTest` | a ViewModel, or a `@Urpc` service, from the production DI graph | everything below it: the server in-process, an embedded database | JVM |
    | `@UnitTest` | one production class over many inputs | nothing; collaborators are test fakes or lambdas | every target |
    | `@ComponentTest` | one screen's content, with fixed state | the renderer | JS (DOM), JVM |
    | `@SnapshotTest` | previews, compared with golden images | the renderer | Android host |
    | `@WiringTest` | a DI graph | the DI container | JVM |
    | `@AdapterTest` | one infrastructure adapter | the infrastructure | JVM |
    | `@CheckTest` | two committed artifacts | files | JVM |
    | `@EndToEndTest` | an application shell, through its UI | the whole application | Desktop JVM |

    Subcutaneous tests are the main suite: most behaviour is covered by driving a ViewModel and
    reading its state. A unit test is for logic with many inputs. A test that needs another kind
    of coverage states it with its category.

    On the JVM each annotation declares a JUnit 5 tag and timeout. `./gradlew unitTest` runs the
    unit tests of every module; each category has its task (`subcutaneousTest`, `componentTest`,
    `wiringTest`, `adapterTest`, `checkTest`, `endToEndTest`, and `snapshotTest`, which needs
    `--no-configuration-cache`). kotlin.test has no tags on JS, wasm or iOS, so `componentTest`
    also runs the module's `jsTest`.

    Component Tests and End To End Tests drive Compose UI screens through [Robots](#robot), one
    per screen, named for what the user does: `clickGreetButton()`, `seeGreeting("Hi there")`.

    Test-support declarations (fakes, fixtures, snapshot surfaces, scenarios, test graphs, robots)
    are Constructs too: every top-level declaration in test sources matches exactly one.
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
        SnapshotSurface,
        Scenario,
        TestGraph,
        Robot,
        Fixture,
    ),
    codeScope = CodeScope.Test,
) {
    @Describe("Every test class (a class declaring a `@Test` function or extending `PreviewSnapshotTestCase`) must have exactly one test category annotation")
    val oneCategoryPerTestClass by rule {
        rationale("The category decides which task runs the test and which rules check it.")
        scope { scope, exempt ->
            scope.classes()
                .filter { test -> test.functions().any { it.hasAnnotationWithName("Test") } || test.parents().any { it.name in TEST_BASES } }
                .filterNot { exempt(it) }
                .mapNotNull { test ->
                    val categories = CATEGORY_ANNOTATIONS.filter { test.hasAnnotationWithName(it) }
                    when (categories.size) {
                        1 -> null
                        0 -> Violation(test, "has no category annotation")
                        else -> Violation(test, "is annotated with ${categories.joinToString { "@$it" }}")
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

private val TEST_BASES = setOf("PreviewSnapshotTestCase")

private val ASSERTION = Regex("""\b(assert\w*|fail|verify|expect\w*|await\w*|see[A-Z]\w*)\s*[(<{]""")

private fun KoFunctionDeclaration.asserts(assertingHelpers: Set<String>): Boolean =
    ASSERTION.containsMatchIn(text) || assertingHelpers.any { helper -> helper != name && Regex("""\b$helper\s*\(""").containsMatchIn(text) }
