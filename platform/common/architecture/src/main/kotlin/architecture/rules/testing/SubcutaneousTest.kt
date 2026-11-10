package architecture.rules.testing

import architecture.definitions.withoutStringsAndComments
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@SubcutaneousTest` class: uses the feature's pages as a user does, through their
    [Robots](#robot), against the production DI graph with the real server running in-process on
    an embedded database. It asserts on what the pages show.

    Subcutaneous tests are the main suite. They exercise the code a user reaches through a page, and
    do not change when the web, domain and data layers below the page are restructured.

    ```kotlin
    @SubcutaneousTest
    class GreetingsPageTest {
        @Test
        fun `a greeting added elsewhere appears on an open page`() = subcutaneous(ukptTestGraph) {
            asUser(UkptPaths.HOME) {
                onGreetingsPage { seeNoGreetings() }

                given { greetingHistory("Kia ora") }

                onGreetingsPage { seeGreetings("Kia ora") }
            }
        }
    }
    ```

    `subcutaneous(graph)` gives each test a new database cloned from the migrated template and
    runs the [Test Graph](#test-graph)'s modules in-process on Ktor's test engine.
    `asUser(path) { }` opens a tab on that server: it sends the requests htmx sends, swaps the
    responses, applies out-of-band swaps and follows event streams, and runs no JavaScript;
    `asUser(path, scripts = false)` checks that the page works without it. `given { }` runs
    [Scenarios](#scenario), before a page opens or while one is open. Each robot step waits up to 10
    seconds of real time.
""")
object SubcutaneousTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("SubcutaneousTest")),
) {
    @Describe("A Subcutaneous Test must live in the `src/test` source set of a `:feature:[name]:tests` module")
    val placement by rule {
        rationale("The `:tests` module holds the feature's test graph, scenarios and robots beside the tests that use them.")
        constrain(
            placement("`:feature:[name]:tests`, `src/test/`") { decl ->
                decl.isInFeatureTestsModule() && decl.sourceSet() == "test"
            },
        )
    }

    @Describe("A Subcutaneous Test must reference only entry-point vocabulary from production code: Web Constants (the paths a user opens), shared domain models and their extensions, domain models, exceptions and constants")
    val entryPointVocabularyOnly by rule {
        rationale(
            """
            The test opens a path and reads what the page shows through a robot. A reference to a
            Routes class, a page function, a use case or a repository fixes that declaration in
            place, and the test changes when the layers below the page are restructured.
            """.trimIndent(),
        )
        note("Test data comes from Scenarios (`given { }`), which may use the server domain.")
        constrainWithScopes { decl, scopes, _ ->
            scopes.referencesOutside(decl, ENTRY_POINT_CONSTRUCTS)
                .map { Violation(decl, "references `${it.name}` (${it.constructs.joinToString { c -> c.id }})") }
        }
    }

    @Describe("Every `@Test` function of a Subcutaneous Test must run its body in `subcutaneous(graph) { … }` and use a page through `asUser(`")
    val runsInTheHarness by rule {
        rationale("The harness starts the server against a new database and tears it down after the test; `asUser` is the tab the robots drive.")
        constrain { decl, _ ->
            (decl as KoClassDeclaration).functions()
                .filter { it.hasAnnotationWithName("Test") }
                .flatMap { test ->
                    val code = test.text.withoutStringsAndComments()
                    HARNESS_CALLS.filterNot { (_, call) -> call.containsMatchIn(code) }
                        .map { (name, _) -> Violation(test, "`${test.name}` does not call `$name`") }
                }
        }
    }

    @Describe("A Subcutaneous Test must not import Koin, Ktor, Exposed, JDBC, jsoup, Playwright or `kotlinx.coroutines.test`")
    val noInfrastructureImports by rule {
        rationale(
            """
            The harness owns the graph, the server, the database and the tab, and robots own the
            markup. A request sent by hand skips the markup that would send it, so the test passes
            while the page's form posts somewhere else. Virtual time from `kotlinx.coroutines.test`
            does not advance real I/O.
            """.trimIndent(),
        )
        constrain(noImportsOf(listOf("org.koin.", "io.ktor.", "org.jetbrains.exposed.", "java.sql.", "javax.sql.", "org.jsoup.", "com.microsoft.playwright.", "kotlinx.coroutines.test.")))
    }
}

private val HARNESS_CALLS = listOf(
    "subcutaneous(" to Regex("""\bsubcutaneous\s*\("""),
    "asUser(" to Regex("""\basUser\s*\("""),
)

private val ENTRY_POINT_CONSTRUCTS = setOf(
    "ServerWeb.WebConstants",
    "ServerDomain.DomainModel",
    "ServerDomain.DomainException",
    "ServerDomain.Constants",
    "ServerDomain.ExtensionFunction",
    "ServerDomain.ExtensionProperty",
    "FeatureRules.SharedDomainModel",
    "FeatureRules.SharedException",
    "FeatureRules.SharedConstants",
    "FeatureRules.SharedExtensionFunction",
    "FeatureRules.SharedExtensionProperty",
)
