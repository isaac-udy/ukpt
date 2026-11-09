package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@WiringTest` class: resolves an application shell's DI graph with Koin's `verify()`, which
    checks that every registered constructor's parameters have a binding. Each shell keeps its
    module list in `ClientDependencies.kt` or `ServerDependencies.kt` so one test covers it.
""")
object WiringTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("WiringTest")),
) {
    @Describe("A Wiring Test must live in an `:app` module's test source set")
    val placement by rule {
        rationale("The `:app` shells assemble the graphs.")
        constrain(placement("an `:app` module") { "/app/" in it.moduleDirectory() })
    }

    @Describe("A Wiring Test must call Koin's `verify()`")
    val resolvesTheGraph by rule {
        rationale("`verify()` resolves every binding without starting the application.")
        constrain { decl, _ ->
            if (Regex("""\.verify\s*\(""").containsMatchIn(decl.fileCode())) emptyList()
            else listOf(Violation(decl, "does not call `verify()`"))
        }
    }
}
