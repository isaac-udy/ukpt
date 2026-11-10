package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    An `@AdapterTest` class: one infrastructure adapter checked against the real infrastructure,
    for behaviour a Subcutaneous Test reaches poorly: transaction semantics, concurrency, a query's
    edge cases. `TransactionRunnerTest` runs against an embedded Postgres. The Subcutaneous
    harness's tab is checked the same way, against a server on Ktor's test engine.
""")
object AdapterTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("AdapterTest")),
) {
    @Describe("An Adapter Test must live in the `src/test` source set of a `:platform:server:*` or `:feature:[name]:server` module, or of `:platform:common:subcutaneous`")
    val placement by rule {
        rationale("The adapters an Adapter Test checks are server infrastructure, and the tab the Subcutaneous harness drives it through.")
        constrain(
            placement("a server module's or `:platform:common:subcutaneous`'s `src/test/`") { decl ->
                val module = decl.moduleDirectory()
                decl.sourceSet() == "test" && (
                    "/platform/server/" in module ||
                        module.endsWith("/platform/common/subcutaneous") ||
                        Regex("/feature/[^/]+/server$").containsMatchIn(module)
                    )
            },
        )
    }

    @Describe("Prefer a Subcutaneous Test; an Adapter Test is for behaviour a Subcutaneous Test cannot reach")
    val rare by guidance {
        rationale("Each Adapter Test fixes an adapter's interface in place, so restructuring the adapter changes the test.")
        audit { decl, _ -> listOf(Violation(decl, "Adapter Test: confirm a Subcutaneous Test cannot cover this behaviour")) }
    }
}
