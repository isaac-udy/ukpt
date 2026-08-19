package architecture.rules.serverservices

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

@Describe("""
    The client-server contract (in `:api`) and its implementation (in `:server`). Services use
    **urpc** (`dev.isaacudy.udytils:urpc-*`): KSP generates the client, the `UrpcService`
    server binding, and the wire descriptors from the annotated interface.

    * **Note:** Service-level exception conventions (dedicated `@Serializable` exception types,
      `PresentableException`, and the deliberate `retryable` flag) are covered by
      `ServerServices.ServiceInterface.errorsViaExceptions` below.
""")
object ServiceInterface : Construct<ServerServices>(
    requirements = listOf(
        isInterfaceWhere("is an `interface` annotated `@Urpc`") { decl -> decl.annotations.any { it.name == "Urpc" } },
        hasNameEndingWith("Service"),
        predicate("resides in `feature.[name].server.services` itself, not in a sub-package") { it.isInServicesRoot() },
    ),
) {
    @Describe(
        "Every `@Urpc` Service contract must be implemented in the feature's `:server` module; a " +
            "Service interface must not be used as a client-only abstraction — client-only " +
            "contracts are domain interfaces in `client.domain`"
    )
    val noClientOnlyServices by rule { unverifiable() }
    @Describe("A Service function must be a plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, taking 0 or 1 parameter")
    val plainFunctionShapes by rule {
        note("The test enforces the parameter count; the suspend/Flow shape is validated by the urpc KSP processor at compile time.")
        constrain { decl, _ ->
            val iface = decl as? KoInterfaceDeclaration ?: return@constrain emptyList()
            iface.functions()
                .filter { it.parameters.size > 1 }
                .map { Violation(it, "service function `${it.name}` takes ${it.parameters.size} parameters — use a single Request type") }
        }
    }

    @Describe("A Service function's `Request`/`Response` types must be nested `@Serializable` types grouped under a per-function `object` namespace")
    val nestedRequestResponseTypes by rule {
        rationale(
            """
            Nesting keeps each function's wire types beside the function and avoids package-level
            `Request`/`Response` name collisions.
            """.trimIndent(),
        )
        unverifiable()
    }

    @Describe("A Service interface must live in `feature.[name].server.services` of the `:api` module")
    val contractLivesInApi by rule {
        constrain { decl, _ ->
            val iface = decl as? KoInterfaceDeclaration ?: return@constrain emptyList()
            if (iface.containingFile.path.contains("/api/src/")) {
                emptyList()
            } else {
                listOf(Violation(iface, "service contract is declared outside the `:api` module"))
            }
        }
    }

    @Describe("A Service function must propagate errors via thrown exceptions; the return type only represents a successful result")
    val errorsViaExceptions by rule {
        rationale(
            """
            urpc uses thrown exceptions as the single failure channel: response types model
            success only, and typed failures cross the wire as `@Serializable` exceptions.
            """.trimIndent(),
        )
        note("Known service exceptions should be their own `@Serializable` type (ideally a `PresentableException`).")
        note("`@Throws` on a `suspend` function must include `kotlin.coroutines.cancellation.CancellationException` (or a superclass such as `Exception`); without it, kotlinc rejects the function on iOS targets.")
        constrain { decl, _ ->
            val iface = decl as? KoInterfaceDeclaration ?: return@constrain emptyList()
            iface.functions()
                .filter { it.hasSuspendModifier }
                .filter { fn -> fn.hasAnnotation { it.name == "Throws" } }
                .filterNot { fn ->
                    val text = fn.annotations.first { it.name == "Throws" }.text
                    text.contains("CancellationException::class") ||
                        Regex("""(?<!\w)Exception::class""").containsMatchIn(text)
                }
                .map { Violation(it, "@Throws on a suspend service function must include CancellationException") }
        }
    }
}
