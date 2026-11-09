package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@ComponentTest` class: renders one screen's `[Name]ScreenContent` with fixed state, then
    reads and drives the rendered output. The Compose HTML screens are tested this way in
    `src/jsTest/` with `html-test-utils`.
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
}

private val COMPONENT_CONSTRUCTS = VALUE_CONSTRUCTS + setOf(
    "ClientUi.Composable",
    "ClientDomain.ExtensionFunction",
    "ClientDomain.ExtensionProperty",
    "FeatureRules.SharedExtensionFunction",
    "FeatureRules.SharedExtensionProperty",
)
