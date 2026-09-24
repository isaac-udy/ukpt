package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isMutable
import architecture.rules.shared.isDomainInterfaceOnSide
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.provider.KoPropertyProvider

@Describe("""
    The values a [Page](#page) renders: a `data class`, sealed type or enum named `[Name]State`,
    built by a handler from domain models and form input.
""")
object ViewState : Construct<ServerWeb>(
    requirements = listOf(
        isClassOrInterface,
        hasNameEndingWith("State"),
        oneOf(isDataClass, isSealed, isEnum),
    ),
) {
    @Describe("A View State must be immutable")
    val immutable by rule {
        constrain { decl, _ ->
            val properties = (decl as? KoPropertyProvider)?.properties().orEmpty()
            properties.filter { it.isMutable() }.map { Violation(it, "View State property `${it.name}` is mutable") }
        }
    }

    @Describe("A View State must not hold a domain interface")
    val holdsValuesOnly by rule {
        rationale("A Page renders a View State without suspending, so a domain interface on it is a call that has not been made.")
        constrain { decl, _ ->
            val properties = (decl as? KoPropertyProvider)?.properties().orEmpty()
            properties
                .filter { isDomainInterfaceOnSide(it.type?.sourceDeclaration as? KoBaseDeclaration, "server") }
                .map { Violation(it, "View State property `${it.name}` is a domain interface") }
        }
    }
}
