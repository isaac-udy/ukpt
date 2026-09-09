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

    @Describe("A Domain Interface should name a capability a consumer asks for, or return a domain model a consumer needs; it should not mirror one storage call, one property of a domain model, or one step of an implementation")
    val namesACapability by guidance {
        rationale(
            """
            A consumer that injects several storage-shaped interfaces and joins their results
            reconstructs a domain model the owning Repository could have produced; the join, and
            the knowledge of which storage produces each part, then repeats in every consumer.
            """.trimIndent(),
        )
        note("Before adding a Domain Interface, name its consumer, the domain result it returns, its provider, and its reason to exist apart from the interfaces beside it. Several interfaces added together for one consumer are a candidate for one Repository property returning one domain model.")
        note("An implementation step with one caller is a private function, a file-private function, or a nested class of that caller, not a Domain Interface.")
        note("Assembling a domain model from storage the feature owns does not permit reading another feature's storage, injecting a sibling Repository, or holding a Domain Interface inside a domain model.")
    }

    @Describe("When a consumer needs several facts about one domain model at once, and those facts share scope, freshness, and failure behaviour, a Domain Interface should return one immutable domain model carrying all of them")
    val readProjections by guidance {
        rationale(
            """
            One read returns one snapshot. Several reads assembled by the consumer return facts
            from different moments, and every consumer decides for itself how a partially loaded
            model behaves.
            """.trimIndent(),
        )
        note("Reads stay separate when a consumer uses one of them alone, when their authorization, freshness, failure, or lifecycle differs, or when one is optional and its failure must not fail the other. Appearing on the same Screen is not a reason to combine reads.")
        note("Returning one data class does not by itself make its facts consistent. When the consumer needs one consistent snapshot, the provider uses one query, one transaction at a suitable isolation level, a shared lock, or a revision, and preserves authorization and tenant scope across every constituent read.")
        note("Queries over one collection that differ only in their filter share one Domain Interface: a nested `sealed interface Input` carries the variants and a default function per variant keeps call sites flat.")
        note("A domain model with lifecycle states is a sealed hierarchy whose variants carry the values each state requires, in place of nullable properties and Booleans that are meaningful only in combination.")
    }

    @Describe("When several mutations act on one domain model and share a return type, prefer a single `Update[Noun]` interface over one interface per mutation: a nested `sealed interface Update` carries the variants, the abstract `invoke(id, update)` is the single entry point, and default functions (`title(...)`, `addMember(...)`) keep call sites flat. When publishing through `:api`, publish exactly the capability another feature needs, never the whole mutation family.")
    val collapsedUpdateFamilies by guidance {
        note("Reads do not join an update family: a read returns the domain model it produces, and reads a consumer needs together form one read projection.")
    }

    @Describe("A mutation should return the value its caller needs next, and no value when an observed read projection already carries the outcome")
    val mutationResults by guidance {
        rationale(
            """
            A caller that receives an identifier and reads the model back performs a second read
            for a value the producer had in hand. A caller that receives a value it never uses
            carries a contract with no consumer.
            """.trimIndent(),
        )
        note("A returned value describes the state captured within the mutation, including whether the mutation was accepted; it does not imply the state is unchanged after the mutation completes.")
    }

    @Describe("A Domain Interface's primary-function parameters must be shared domain models, the layer's own domain models, nested types, primitives, standard date/time value types, collections of those, or a `Flow` of those")
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

    @Describe("A Domain Interface's primary-function return type must be shared domain models, the layer's own domain models, nested types, primitives, standard date/time value types, collections of those, a `Flow` of those, or no value")
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
