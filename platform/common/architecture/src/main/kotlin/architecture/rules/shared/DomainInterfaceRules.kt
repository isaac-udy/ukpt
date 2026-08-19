package architecture.rules.shared

import architecture.utils.isDomainCompatibleType
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import dev.isaacudy.udytils.architecture.*

/**
 * The DomainInterface rules shared by both sided domain groups. What differs per side is how an
 * interface is *satisfied* — the client accepts a Repository property or a UseCase, the server
 * additionally accepts IntegrationClient-shaped adapters — so each concrete object declares its
 * own provided-by rule, ending in [providedByCheck] with its side's adapter suffixes.
 */
abstract class DomainInterfaceRules<G : RuleGroup> : Construct<G>(
    requirements = listOf(
        isInterfaceWhere("is a `fun interface`") { it.hasFunModifier && !it.hasSealedModifier },
        isInterfaceWhere("has a primary function that is an `operator fun invoke`") { decl ->
            decl.functions().any { it.name == "invoke" && it.hasOperatorModifier }
        },
        isInterfaceWhere("declares all functions as `suspend` or returning a `Flow<T>`") { decl ->
            decl.functions()
                .filter { it.name == "invoke" || !it.text.contains("=") }
                .all { it.hasSuspendModifier || isFlowTypeName(it.returnType?.name) }
        },
        isInterfaceWhere("is prefixed with `FlowOf` when its primary function returns a `Flow`") { decl ->
            val hasFlowReturn = decl.functions().any { it.name == "invoke" && isFlowTypeName(it.returnType?.name) }
            !hasFlowReturn || decl.name.startsWith("FlowOf")
        },
    ),
) {
    @Describe("A Domain Interface may define additional default functions that call the primary function")
    val interfaceDefaults by guidance

    @Describe("When several mutations act on one domain model and share a return type, prefer a single `Update[Noun]` interface over one interface per mutation: a nested `sealed interface Update` carries the variants, the abstract `invoke(id, update)` is the single entry point, and default functions (`title(...)`, `addMember(...)`) keep call sites flat. Reads stay separate interfaces — their return types differ. When publishing through `:api`, publish exactly the capability another feature needs, never the whole mutation family.")
    val collapsedUpdateFamilies by guidance

    @Describe("A Domain Interface's primary-function parameters must be shared domain models, side-private domain models, nested types, primitives, standard date/time value types, collections of those, or a `Flow` of those")
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

    @Describe("A Domain Interface's primary-function return type must be shared domain models, side-private domain models, nested types, primitives, standard date/time value types, collections of those, a `Flow` of those, or no value")
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

    @Describe("A Domain Interface's functions must propagate errors via thrown exceptions, never via the return type")
    val errorsViaExceptions by rule {
        rationale(
            """
            A result type that carries the failure makes every caller unwrap it, and the layer's
            vocabulary grows a wrapper around each contract. Thrown exceptions keep the primary
            function's return type the thing it produces.
            """.trimIndent(),
        )
        note("Known exceptions should be their own type extending RuntimeException, marked with `@Throws`.")
        note("`@Throws` on a `suspend` function must include `kotlin.coroutines.cancellation.CancellationException` (or a superclass such as `Exception`): an interface published to `:api` compiles for every target, and kotlinc rejects the function on iOS without it.")
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

    /**
     * The provided-by check: every classified interface is either implemented by a class (a
     * UseCase, or an adapter that satisfies it directly) or referenced from a property of a class
     * whose name ends in one of [adapterSuffixes]. [violation] renders the side's message for an
     * unsatisfied interface's name.
     */
    protected fun providedByCheck(adapterSuffixes: List<String>, violation: (String) -> String): ScopeCheck =
        ScopeCheck { scope, exempt ->
            scope.interfaces()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .filterNot { iface ->
                    val implementedByClass = scope.classes().any { cls -> cls.parents().any { it.name == iface.name } }
                    val exposedByAdapter = scope.classes()
                        .filter { cls -> adapterSuffixes.any { cls.name.endsWith(it) } }
                        .any { adapter -> adapter.properties().any { prop -> prop.text.contains(iface.name) } }
                    implementedByClass || exposedByAdapter
                }
                .map { Violation(it, violation(it.name)) }
        }
}
