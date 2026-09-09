package architecture.rules.shared

import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

/**
 * The UseCase rules, declared once and instantiated by each sided domain group's concrete
 * `object UseCase : UseCaseRules<Group>(side)`. The group's package gate does the scoping of the
 * rules; [side] scopes only the dependency audit to the side's classified domain interfaces. The
 * base carries the whole discipline; a concrete object adds only its side's narrative and any
 * side-specific rules.
 */
abstract class UseCaseRules<G : RuleGroup>(
    private val side: String,
) : Construct<G>(
    requirements = listOf(
        isClassWhere("is a non-sealed/data/enum/value class named `[DomainInterface]Impl`") { decl ->
            !decl.hasSealedModifier && !decl.hasDataModifier && !decl.hasEnumModifier && !decl.hasValueModifier &&
                decl.name == "${decl.associatedDomainInterfaceName()}Impl"
        },
        isClassWhere("implements exactly one domain interface") { it.associatedDomainInterfaceName() != null },
    ),
) {
    @Describe("A UseCase must not contain mutable state: all properties must be `val`")
    val noMutableState by rule {
        rationale(
            """
            A UseCase instance is shared by its consumers and may be invoked concurrently; a `var`
            property lets one invocation change another's behaviour or internal state.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties().filter { it.isMutable() }.map { Violation(it, "UseCase has a mutable (`var`) property — all UseCase properties must be `val`") }
        }
    }

    @Describe("A UseCase must not override any default function of its domain interface")
    val noOverridingDefaults by rule {
        rationale(
            """
            The only abstract member of a domain interface is the primary `operator fun invoke`;
            every other function is a default. Default functions are contract behaviour built on
            `invoke`; overriding one makes the same helper behave differently depending on which
            implementation is injected.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.functions()
                .filter { it.hasOverrideModifier }
                .filterNot { it.name == "invoke" }
                .map { Violation(it, "UseCase overrides a default interface function") }
        }
    }

    @Describe("A UseCase may inject domain interfaces to perform its logic")
    val mayInjectDomainInterfaces by guidance

    @Describe("A UseCase that becomes too complex should be broken into private, file-private, or nested parts")
    val breakDownComplexUseCases by guidance

    @Describe("A UseCase should exist for a decision, or for a composition of capabilities that exist independently of it, not to forward one call")
    val existsForADecision by guidance {
        rationale("A UseCase over one domain interface adds a class, a binding, and a contract between the caller and that one dependency; the same logic as a default function of the dependency's interface, or as the dependency's own Repository property, adds none of them.")
        note("The audit reports a UseCase whose primary constructor takes exactly one domain interface of its side. Authorization wrappers and error translation are the usual reasons such a UseCase stays.")
        auditScope { scope, exempt ->
            val fqns = scope.domainInterfaceFqnsOnSide(side)
            scope.classes()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .mapNotNull { cls ->
                    val consumed = consumedInterfaces(cls, fqns)
                    if (consumed.size != 1) return@mapNotNull null
                    val dependency = consumed.single().substringAfterLast('.')
                    Violation(cls, "`${cls.name}` injects one domain interface, `$dependency`. Could the logic be a default function of `$dependency`, or a property of the Repository that provides it?")
                }
        }
    }
}

/**
 * The single domain interface a UseCase class implements, or null if it isn't exactly one.
 *
 * Matched by name: a UseCase is a `<X>Impl` class with exactly one parent whose simple name is
 * `<X>`. We deliberately do NOT call `DomainInterface.test()` on the parent — `parents()` yields a
 * parent *reference*, not the resolved interface declaration, so the interface predicates can't run
 * against it, and in the normal case the interface lives in the sibling `:api` module and can't be
 * resolved from the class alone. The "`<X>` is really a domain interface" guarantee is carried by
 * each side's scope-level provided-by rule on its DomainInterface construct.
 */
private fun KoClassDeclaration.associatedDomainInterfaceName(): String? {
    val parents = this.parents()
    if (parents.size != 1) return null
    val parentName = parents.single().name
    return if (name == "${parentName}Impl") parentName else null
}
