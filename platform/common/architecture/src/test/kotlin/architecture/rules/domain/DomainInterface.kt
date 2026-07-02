package architecture.rules.domain

import architecture.registry.*

import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

@Describe("""
    A functional interface representing domain-level functionality/business logic.

    * **Note**: Default functions don't need to be `operator fun invoke` and should use
      expressive names; they should provide commonly used functionality (e.g. handling a
      particular exception type) or simplify calling the primary function with particular
      parameters.
    * **Note**: Implementations must never override an interface's default functions;
      convenience functions belong as default members, not top-level extensions, so they're
      discoverable and co-located with the interface.
    * **Note**: Generic/unknown errors don't need their own exception type or `@Throws` entry.
""")
object DomainInterface : Construct<DomainLayer>(
    requirements = listOf(
        isInterfaceWhere("Domain interfaces must be a `fun interface`") { it.hasFunModifier && !it.hasSealedModifier },
        isInterfaceWhere("The primary function of a domain interface must be an `operator fun invoke`") { decl ->
            decl.functions().any { it.name == "invoke" && it.hasOperatorModifier }
        },
        isInterfaceWhere("All functions in a domain interface must be `suspend` or return a `Flow<T>`") { decl ->
            decl.functions()
                .filter { it.name == "invoke" || !it.text.contains("=") }
                .all { it.hasSuspendModifier || it.returnType?.name?.contains("Flow") == true }
        },
        isInterfaceWhere("Flow-returning domain interfaces are prefixed with `FlowOf`") { decl ->
            val hasFlowReturn = decl.functions().any { it.name == "invoke" && it.returnType?.name?.contains("Flow") == true }
            !hasFlowReturn || decl.name.startsWith("FlowOf")
        },
    ),
) {
    @Describe("May define additional default functions that call the primary function")
    val interfaceDefaults by guidance
    @Describe("Primary-function parameters must be domain objects, nested types, primitives, or collections of those")
    val primaryParameterTypes by guidance
    @Describe("Primary-function return type must be domain objects, nested types, primitives, collections of those, or no value")
    val primaryReturnType by guidance
    @Describe("Must be implemented by a Repository (as a property) or by a UseCase")
    val implementedByRepositoryOrUseCase by guidance

    @Describe("Functions propagate errors via thrown exceptions, never via the return type")
    val errorsViaExceptions by rule {
        rationale(
            """
            @Throws on suspend functions must include CancellationException (or a superclass like
            Exception) — required for Kotlin/Native: kotlinc rejects the function on iOS targets otherwise.
            """.trimIndent(),
        )
        note("Known exceptions should be their own type extending RuntimeException, marked with `@Throws`.")
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
                .map { Violation(it, "@Throws on a suspend function must include CancellationException") }
        }
    }
}
