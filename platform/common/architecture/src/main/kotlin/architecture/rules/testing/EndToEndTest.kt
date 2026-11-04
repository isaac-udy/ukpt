package architecture.rules.testing

import architecture.definitions.withoutStringsAndComments
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    An `@EndToEndTest` class: drives the desktop application's root composable through its UI with
    Compose's `runComposeUiTest`, against the server running in-process. It covers what a
    Subcutaneous Test does not: that screens bind to their ViewModels and that the shell starts.

    ```kotlin
    @Test
    fun `a seeded greeting shows`() = inProcessServer(::serverDependencies) {
        given { greetingHistory("Hi there") }
        val rootContext = appRootContext()

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalRootContext provides rootContext) {
                    KoinApplication(application = { modules(clientDependencies + clientEdges) }) {
                        AppContent()
                    }
                }
            }
            awaitText("Hi there")
        }
    }
    ```

    `inProcessServer(server)` starts the server graph on Ktor's test engine over a new database and
    builds no client graph; `clientEdges` points the application's own graph at it. Teardown stops
    the Koin context `KoinApplication` started.
""")
object EndToEndTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("EndToEndTest")),
) {
    @Describe("An End To End Test must live in `:app:client:desktop`'s `src/test` source set")
    val placement by rule {
        rationale("The desktop shell is the one application that runs the UI and the server in one JVM.")
        constrain(placement("`:app:client:desktop`, `src/test/`") { it.moduleDirectory().endsWith("/app/client/desktop") && it.sourceSet() == "test" })
    }

    @Describe("Every `@Test` function of an End To End Test must call `inProcessServer(`, `runComposeUiTest` and `AppContent(`")
    val drivesTheAppRoot by rule {
        rationale(
            """
            The test covers the application as it starts: its root composable and its own client
            graph, against the real server. A test that renders one screen, or builds the client
            graph itself, is a Component or Subcutaneous Test.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            (decl as KoClassDeclaration).functions()
                .filter { it.hasAnnotationWithName("Test") }
                .flatMap { test ->
                    val code = test.text.withoutStringsAndComments()
                    APP_ROOT_CALLS.filterNot { (_, call) -> call.containsMatchIn(code) }
                        .map { (name, _) -> Violation(test, "`${test.name}` does not call `$name`") }
                }
        }
    }
}

private val APP_ROOT_CALLS = listOf(
    "inProcessServer(" to Regex("""\binProcessServer\s*\("""),
    "runComposeUiTest" to Regex("""\brunComposeUiTest\s*[({]"""),
    "AppContent(" to Regex("""\bAppContent\s*\("""),
)
