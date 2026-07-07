package architecture.rules.domain

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    A class that implements a single [domain interface](#domain-interface).

    * **Note:** Immutable helper properties, such as loggers, are permitted. "No mutable state"
      forbids `var` properties, not properties in general.
    * **Note:** If a UseCase only injects a single other domain interface, consider whether that
      logic should become a default function of the other domain interface instead.
    * **Note:** When breaking down a complex UseCase, use file-private extension functions,
      private functions, or nested classes instead of additional domain interfaces or UseCases.
""")
object UseCase : Construct<DomainLayer>(
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
            every other function is a default. Overriding a default in an implementation defeats
            the purpose of the interface helpers.
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
}

/**
 * The single domain interface a UseCase class implements, or null if it isn't exactly one.
 *
 * Matched by name: a UseCase is a `<X>Impl` class with exactly one parent whose simple name is
 * `<X>`. We deliberately do NOT call `DomainInterface.test()` on the parent — `parents()` yields a
 * parent *reference*, not the resolved interface declaration, so the interface predicates can't run
 * against it, and in the normal case the interface lives in the sibling `:api` module and can't be
 * resolved from the class alone. The "`<X>` is really a domain interface" guarantee is carried by
 * the scope-level [DomainInterface.implementedByRepositoryOrUseCase] rule.
 */
private fun KoClassDeclaration.associatedDomainInterfaceName(): String? {
    val parents = this.parents()
    if (parents.size != 1) return null
    val parentName = parents.single().name
    return if (name == "${parentName}Impl") parentName else null
}
