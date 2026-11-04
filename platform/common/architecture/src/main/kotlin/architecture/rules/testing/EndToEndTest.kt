package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    An `@EndToEndTest` class: drives the desktop application's root composable through its UI with
    Compose's `runComposeUiTest`, against the server running in-process. It covers what a
    Subcutaneous Test does not: that screens bind to their ViewModels and that the shell starts.
""")
object EndToEndTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("EndToEndTest")),
) {
    @Describe("An End To End Test must live in `:app:client:desktop`'s `src/test` source set")
    val placement by rule {
        rationale("The desktop shell is the one application that runs the UI and the server in one JVM.")
        constrain(placement("`:app:client:desktop`, `src/test/`") { it.moduleDirectory().endsWith("/app/client/desktop") && it.sourceSet() == "test" })
    }
}
