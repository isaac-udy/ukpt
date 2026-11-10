package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@ComponentTest` class: renders one Page, Component or Event Stream from `server.web` with
    fixed state, then reads the markup with jsoup.

    ```kotlin
    @Test
    fun `the first event replaces the list, later events append and remove`() = runTest {
        val events = greetingEvents { flowOf(listOf(ada), listOf(ada, grace)) }.toList()
        …
    }
    ```

    A domain interface the rendered code takes is a lambda. A Component Test is for markup with
    many states, such as the out-of-band swaps an event stream sends; what a user sees on a page is
    covered by a [Subcutaneous Test](#subcutaneous-test).
""")
object ComponentTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("ComponentTest")),
) {
    @Describe("A Component Test must live in the `src/test` source set of a `:feature:[name]:server` module or `:platform:server:web`")
    val placement by rule {
        rationale("The pages, components and event streams of `server.web` are `internal` to their module.")
        constrain(
            placement("a `:feature:[name]:server` module's or `:platform:server:web`'s `src/test/`") { decl ->
                decl.sourceSet() == "test" && decl.isInServerWebModule()
            },
        )
    }

    @Describe("A Component Test must reference only the web layer's markup, its View States and Forms, domain interfaces (as lambdas), and the value types they carry, and must not import dependency injection or Ktor's test engine")
    val rendersMarkupOnly by rule {
        rationale(
            """
            The test fixes the state and reads what renders. A Routes class, a repository, the DI
            graph or a running application in the test makes it a Subcutaneous or Adapter Test.
            """.trimIndent(),
        )
        constrainWithScopes { decl, scopes, _ ->
            scopes.referencesOutside(decl, COMPONENT_CONSTRUCTS).map { Violation(decl, "references `${it.name}` (${it.constructs.joinToString { c -> c.id }})") } +
                decl.file.imports
                    .filter { it.name.startsWith("org.koin.") || it.name.startsWith("io.ktor.server.testing.") }
                    .map { Violation(decl, "imports `${it.name}`") }
        }
    }
}

private val COMPONENT_CONSTRUCTS = VALUE_CONSTRUCTS + setOf(
    "ServerWeb.Page",
    "ServerWeb.Layout",
    "ServerWeb.Component",
    "ServerWeb.EventStream",
    "ServerWeb.ViewHelper",
    "ServerWeb.WebConstants",
    "ServerDomain.DomainInterface",
    "ServerDomain.ExtensionFunction",
    "ServerDomain.ExtensionProperty",
    "FeatureRules.SharedExtensionFunction",
    "FeatureRules.SharedExtensionProperty",
)
