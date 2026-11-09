package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@ComponentTest` class: renders one screen's `[Name]ScreenContent` with fixed state, then
    reads and drives the rendered output through the screen's [Robot](#robot).

    ```kotlin
    @Test
    fun `a summary shows the latest greeting`() = runComposeUiTest {
        setContent { UkptTheme { UkptScreenContent(state = summaryState, onGreet = {}, onRetry = {}, onReset = {}) } }
        asUser {
            onUkptScreen { seeGreeting("Hi there") }
        }
    }
    ```

    The Compose HTML screens are tested in `src/jsTest/` with `html-test-utils`, which reads the
    DOM; robots drive Compose UI only.
""")
object ComponentTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("ComponentTest")),
) {
    @Describe("A Component Test must live in a `:client` module's `jsTest`, `jvmTest` or `desktopTest` source set")
    val placement by rule {
        rationale("`[Name]ScreenContent` is `internal` to its `:client` module.")
        constrain(
            placement("a `:client` module's `jsTest`, `jvmTest` or `desktopTest`") { decl ->
                decl.moduleDirectory().endsWith("/client") && decl.sourceSet() in setOf("jsTest", "jvmTest", "desktopTest")
            },
        )
    }

    @Describe("A Component Test must reference only composables, ViewModel states, and the value types they carry from production code, and must not import dependency injection")
    val rendersScreenContentOnly by rule {
        rationale(
            """
            The test fixes the state and reads what renders. A ViewModel, a repository, or the DI
            graph in the test makes it a Subcutaneous Test.
            """.trimIndent(),
        )
        constrainWithScopes { decl, scopes, _ ->
            scopes.referencesOutside(decl, COMPONENT_CONSTRUCTS).map { Violation(decl, "references `${it.name}` (${it.constructs.joinToString { c -> c.id }})") } +
                decl.file.imports.filter { it.name.startsWith("org.koin.") }.map { Violation(decl, "imports `${it.name}`") }
        }
    }

    @Describe("A Compose UI Component Test must find and act on nodes only through Robots: no `onNode…`, `onAllNodes…`, `onRoot`, `perform…` or node `waitUntil…` calls")
    val drivesThroughRobots by rule {
        rationale("The robot owns the screen's tags, so the Component Test checks the same steps the End To End Tests take.")
        note("A Component Test in `jsTest` renders Compose HTML and is not checked.")
        constrain { decl, exempt -> if (decl.sourceSet() == "jsTest") emptyList() else nodesOnlyThroughRobots.run(decl, exempt) }
    }
}

private val COMPONENT_CONSTRUCTS = VALUE_CONSTRUCTS + setOf(
    "ClientUi.Composable",
    "ClientDomain.ExtensionFunction",
    "ClientDomain.ExtensionProperty",
    "FeatureRules.SharedExtensionFunction",
    "FeatureRules.SharedExtensionProperty",
)
