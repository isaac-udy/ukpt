package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    An `@AdapterTest` class: one infrastructure adapter checked against the real infrastructure,
    for behaviour a Subcutaneous Test covers poorly: transaction semantics, concurrency, a query's
    edge cases. `TransactionRunnerTest` runs against an embedded Postgres.

    A client's adapters are the device's files, audio, sensors and document renderers, checked from
    a platform test source set (`jvmTest`, `iosSimulatorArm64Test`, `androidHostTest`, …) of the
    module that owns them. An Android adapter in `androidMain` is checked from `androidHostTest`,
    the only host test set that compiles against it; it runs with that source set's test task, so
    `snapshotTest` runs it, not `adapterTest`.
""")
object AdapterTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("AdapterTest")),
) {
    @Describe("An Adapter Test must live in the `src/test` source set of a `:platform:server:*` or `:feature:[name]:server` module, of `:app:server` when it spans more than one feature, or a platform test source set of a `:platform:client:*` or `:feature:[name]:client` module")
    val placement by rule {
        rationale("Server adapters run on the JVM; a client adapter runs only on the platform it adapts, so its test can't live in `commonTest`. A feature `:server` may not depend on another, so a test of two features' adapters together lives in the server shell that composes them.")
        constrain(
            placement("a server module's `src/test/`, or a client module's platform test source set") { decl ->
                val module = decl.moduleDirectory()
                val sourceSet = decl.sourceSet()
                val serverModule = "/platform/server/" in module || FEATURE_SERVER_MODULE.containsMatchIn(module)
                val clientModule = "/platform/client/" in module || FEATURE_CLIENT_MODULE.containsMatchIn(module)
                ((serverModule || module.endsWith("/app/server")) && sourceSet == "test") ||
                    (clientModule && sourceSet.endsWith("Test") && sourceSet != "commonTest")
            },
        )
    }

    @Describe("Prefer a Subcutaneous Test; an Adapter Test is for behaviour a Subcutaneous Test cannot cover")
    val rare by guidance {
        rationale("Each Adapter Test fixes an adapter's interface in place, so restructuring the adapter changes the test.")
        audit { decl, _ -> listOf(Violation(decl, "Adapter Test: confirm a Subcutaneous Test cannot cover this behaviour")) }
    }
}

private val FEATURE_SERVER_MODULE = Regex("/feature/(?:[^/]+/)+server$")
private val FEATURE_CLIENT_MODULE = Regex("/feature/(?:[^/]+/)+client$")
