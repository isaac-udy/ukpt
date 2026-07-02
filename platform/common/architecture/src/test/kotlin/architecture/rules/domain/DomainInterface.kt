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
        isInterfaceWhere("is a `fun interface`") { it.hasFunModifier && !it.hasSealedModifier },
        isInterfaceWhere("has a primary function that is an `operator fun invoke`") { decl ->
            decl.functions().any { it.name == "invoke" && it.hasOperatorModifier }
        },
        isInterfaceWhere("declares all functions as `suspend` or returning a `Flow<T>`") { decl ->
            decl.functions()
                .filter { it.name == "invoke" || !it.text.contains("=") }
                .all { it.hasSuspendModifier || it.returnType?.name?.contains("Flow") == true }
        },
        isInterfaceWhere("is prefixed with `FlowOf` when its primary function returns a `Flow`") { decl ->
            val hasFlowReturn = decl.functions().any { it.name == "invoke" && it.returnType?.name?.contains("Flow") == true }
            !hasFlowReturn || decl.name.startsWith("FlowOf")
        },
    ),
) {
    @Describe("A Domain Interface may define additional default functions that call the primary function")
    val interfaceDefaults by guidance

    @Describe("A Domain Interface's primary-function parameters must be domain objects, nested types, primitives, or collections of those")
    val primaryParameterTypes by rule {
        constrain { decl, _ ->
            val iface = decl as? KoInterfaceDeclaration ?: return@constrain emptyList()
            iface.functions()
                .filter { it.name == "invoke" && it.hasOperatorModifier }
                .flatMap { fn ->
                    fn.parameters
                        .filterNot { isDomainCompatibleType(it.type.name, iface.containingFile) }
                        .map { Violation(fn, "primary-function parameter `${it.name}: ${it.type.name}` is not a domain-compatible type") }
                }
        }
    }

    @Describe("A Domain Interface's primary-function return type must be domain objects, nested types, primitives, collections of those, or no value")
    val primaryReturnType by rule {
        constrain { decl, _ ->
            val iface = decl as? KoInterfaceDeclaration ?: return@constrain emptyList()
            iface.functions()
                .filter { it.name == "invoke" && it.hasOperatorModifier }
                .mapNotNull { fn ->
                    val returnType = fn.returnType ?: return@mapNotNull null
                    if (isDomainCompatibleType(returnType.name, iface.containingFile)) {
                        null
                    } else {
                        Violation(fn, "primary-function return type `${returnType.name}` is not a domain-compatible type")
                    }
                }
        }
    }

    @Describe("A Domain Interface must be implemented by a Repository (as a property) or by a UseCase")
    val implementedByRepositoryOrUseCase by rule {
        note("The check accepts either a class whose parents include the interface (a UseCase) or a `[Name]Repository` whose properties reference the interface.")
        scope { scope, exempt ->
            scope.interfaces()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .filterNot { iface ->
                    val implementedByClass = scope.classes().any { cls -> cls.parents().any { it.name == iface.name } }
                    val exposedByRepository = scope.classes()
                        .filter { it.name.endsWith("Repository") }
                        .any { repo -> repo.properties().any { prop -> prop.text.contains(iface.name) } }
                    implementedByClass || exposedByRepository
                }
                .map { Violation(it, "domain interface `${it.name}` has no Repository property or UseCase implementation") }
        }
    }

    @Describe("A Domain Interface's functions propagate errors via thrown exceptions, never via the return type")
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
