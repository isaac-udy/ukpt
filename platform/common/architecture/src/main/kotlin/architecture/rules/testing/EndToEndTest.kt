package architecture.rules.testing

import architecture.definitions.withoutStringsAndComments
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    An `@EndToEndTest` class: drives the server's pages in headless Chromium through Playwright,
    against the server serving on a local port. It covers what a Subcutaneous Test does not: the
    scripts on a page (htmx itself, Alpine, the project's own files), the content security policy,
    and the server starting from `serverDependencies`.

    ```kotlin
    @Test
    fun `greeting someone`() = inProcessServer(::serverDependencies) {
        given { greetingHistory("Hello, Grace") }

        asUserInBrowser(UkptPaths.HOME) {
            onGreetingsPage {
                enterName("Ada")
                seeCharactersLeft("37 characters left")
                clickGreetButton()
                seeGreetings("Hello, Grace", "Hello, Ada")
            }
        }
    }
    ```

    `inProcessServer(server)` starts the server graph on a free port over a new database.
    `asUserInBrowser(path) { }` opens a browser tab there and fails afterwards if the page logged
    an error to the console, which is where Chromium reports a content security policy violation.
    Each step goes through a page's [Robot](#robot), the same one the Subcutaneous Tests use.
    `ukpt.end-to-end-tests` installs the Chromium headless shell before the tests run.
""")
object EndToEndTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("EndToEndTest")),
) {
    @Describe("An End To End Test must live in `:app:server`'s `src/test` source set")
    val placement by rule {
        rationale("`:app:server` assembles the whole graph, `serverDependencies`, that the test serves.")
        constrain(placement("`:app:server`, `src/test/`") { it.moduleDirectory().endsWith("/app/server") && it.sourceSet() == "test" })
    }

    @Describe("Every `@Test` function of an End To End Test must call `inProcessServer(` and `asUserInBrowser(`")
    val drivesTheServerInABrowser by rule {
        rationale(
            """
            The test covers the application as a browser loads it, against the real server. A test
            that sends requests itself, or drives the Subcutaneous tab, is a Subcutaneous Test.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            (decl as KoClassDeclaration).functions()
                .filter { it.hasAnnotationWithName("Test") }
                .flatMap { test ->
                    val code = test.text.withoutStringsAndComments()
                    BROWSER_CALLS.filterNot { (_, call) -> call.containsMatchIn(code) }
                        .map { (name, _) -> Violation(test, "`${test.name}` does not call `$name`") }
                }
        }
    }

    @Describe("An End To End Test must find and act on elements only through Robots: no Playwright, jsoup or Ktor client imports")
    val drivesThroughRobots by rule {
        rationale("A journey reads as one block of user steps per page, and a page's markup changes in one place, its robot.")
        constrain(noImportsOf(listOf("com.microsoft.playwright.", "org.jsoup.", "io.ktor.client.")))
    }
}

private val BROWSER_CALLS = listOf(
    "inProcessServer(" to Regex("""\binProcessServer\s*\("""),
    "asUserInBrowser(" to Regex("""\basUserInBrowser\s*\("""),
)
