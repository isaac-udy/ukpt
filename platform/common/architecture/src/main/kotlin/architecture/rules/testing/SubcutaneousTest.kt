package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@SubcutaneousTest` class: drives a real entry point (a ViewModel, or a `@Urpc` service for
    server behaviour that no screen reaches) built from the production DI graph, with the real
    server running in-process against an embedded database. It asserts on what the entry point
    exposes: ViewModel state, navigation results, service responses.

    Subcutaneous tests are the main suite. They exercise the code a user reaches through a screen,
    and do not change when the domain and data layers below the ViewModel are restructured.
""")
object SubcutaneousTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("SubcutaneousTest")),
) {
    @Describe("A Subcutaneous Test must live in the `src/test` source set of a `:feature:[name]:tests` module")
    val placement by rule {
        rationale(
            """
            The `:tests` module depends on the feature's `:client` and `:server`, which no other
            module may combine.
            """.trimIndent(),
        )
        constrain(
            placement("`:feature:[name]:tests`, `src/test/`") { decl ->
                decl.isInFeatureTestsModule() && decl.sourceSet() == "test"
            },
        )
    }
}
