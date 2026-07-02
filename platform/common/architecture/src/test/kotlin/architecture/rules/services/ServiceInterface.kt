package architecture.rules.services

import architecture.registry.*

import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

@Describe("""
    The client-server contract (in `:api`) and its implementation (in `:server`). Services use
    **urpc** (`dev.isaacudy.udytils:urpc-*`): KSP generates the client, the `UrpcService`
    server binding, and the wire descriptors from the annotated interface.

    * **Note**: Service-level exception conventions — dedicated `@Serializable` exception
      types, `PresentableException`, and the deliberate `retryable` flag — are covered in
      [exception handling](exceptions.md).
""")
object ServiceInterface : Construct<ServicesLayer>(
    requirements = listOf(
        isInterfaceWhere("is an `interface` annotated `@Urpc`") { decl -> decl.annotations.any { it.name == "Urpc" } },
        hasNameEndingWith("Service"),
        predicate("resides in the top-level `feature.[name].services` package") { it.isInServicesRoot() },
    ),
) {
    @Describe("A Service must always be implemented as urpc service functions in the appropriate server module — never as a client-only local service")
    val noClientOnlyServices by guidance
    @Describe("A Service function is a plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, taking 0 or 1 parameter")
    val plainFunctionShapes by rule {
        note("The check enforces the parameter count; the suspend/Flow shape is validated by the urpc KSP processor at compile time.")
        constrain { decl, _ ->
            val iface = decl as? KoInterfaceDeclaration ?: return@constrain emptyList()
            iface.functions()
                .filter { it.parameters.size > 1 }
                .map { Violation(it, "service function `${it.name}` takes ${it.parameters.size} parameters — use a single Request type") }
        }
    }

    @Describe("A Service function's `Request`/`Response` types are nested `@Serializable` types grouped under a per-function `object` namespace")
    val nestedRequestResponseTypes by guidance

    @Describe("A Service interface lives in `feature.[name].services` of the `:api` module")
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

    @Describe("A Service function propagates errors via thrown exceptions; the return type only ever represents a successful result")
    val errorsViaExceptions by rule {
        rationale(
            """
            @Throws on suspend functions must include CancellationException (or a superclass like
            Exception) — required for Kotlin/Native: kotlinc rejects the function on iOS targets otherwise.
            """.trimIndent(),
        )
        note("Known service exceptions should be their own `@Serializable` type (ideally a `PresentableException`).")
        note("`@Throws` on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException`.")
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
