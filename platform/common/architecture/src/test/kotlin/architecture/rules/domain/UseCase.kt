package architecture.rules.domain

import architecture.registry.*

import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    A class that implements a single [domain interface](#domain-interface).

    * **Note**: Immutable helper properties (e.g., loggers) are permitted — "no mutable state"
      forbids `var` properties, not properties in general.
    * **Note**: If a UseCase only injects a single other domain interface, consider whether
      that logic should become a default function of the other domain interface instead.
    * **Note**: When breaking down a complex UseCase, reach for file-private extension
      functions, private functions, or nested classes — not additional domain
      interfaces/UseCases that pollute the namespace.
""")
object UseCase : Construct<DomainLayer>(
    requirements = listOf(
        isClassWhere("A UseCase is a non-sealed/data/enum/value class named `[DomainInterface]Impl`") { decl ->
            !decl.hasSealedModifier && !decl.hasDataModifier && !decl.hasEnumModifier && !decl.hasValueModifier &&
                decl.name == "${decl.associatedDomainInterfaceName()}Impl"
        },
        isClassWhere("A UseCase must implement exactly one domain interface") { it.associatedDomainInterfaceName() != null },
    ),
) {
    @Describe("A UseCase must not contain mutable state — all properties are `val`")
    val noMutableState by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties().filter { it.isMutable() }.map { Violation(it, "UseCase has a mutable (`var`) property — all UseCase properties must be `val`") }
        }
    }

    @Describe("Must not override any default function of its domain interface")
    val noOverridingDefaults by rule {
        rationale(
            """
            The only abstract member is the primary `operator fun invoke`; every other function is a
            default. Overriding a default per-implementation defeats the point of the interface helpers.
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

    @Describe("May inject domain interfaces to perform its logic")
    val mayInjectDomainInterfaces by guidance
    @Describe("If it becomes too complex, break it into private/file-private/nested parts")
    val breakDownComplexUseCases by guidance
}

/** The single domain interface a UseCase class implements, or null if it isn't exactly one. */
private fun KoClassDeclaration.associatedDomainInterfaceName(): String? {
    val parents = this.parents()
    if (parents.size != 1) return null
    val parent = parents.single()
    return if (DomainInterface.test(parent)) parent.name else null
}
