package architecture.rules.ui

import architecture.registry.*

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider

@Describe("""
    A serializable data class or object representing the navigation contract for a particular
    screen; the input parameters required by that screen (if any) and the output result type
    provided by that screen (if any).

    * **Note**: "Minimal data" means identifiers, not payloads — a Destination should accept a
      `User.Id` and let the Screen load the associated `User`, rather than accepting an entire
      `User`.
""")
object Destination : Construct<UiLayer>(
    requirements = listOf(
        isClassOrObject,
        predicate("Destinations must implement `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>`") { d ->
            (d is KoClassDeclaration || d is KoObjectDeclaration) &&
                d.parents().any { parent -> parent.name.contains("NavigationKey") }
        },
        hasNameEndingWith("Destination"),
        isAnnotatedWith("Serializable"),
        hasFileNameMatchingDeclaration,
    ),
) {
    @Describe("Destinations should accept the minimal data required to initialise the associated Screen")
    val minimalData by guidance
    @Describe("Destinations may live in `:api` (shared entry point / server-driven) or `:client` (internal only)")
    val definedInApiOrClient by rule {
        constrain { decl, _ ->
            val path = (decl as? KoContainingFileProvider)?.containingFile?.path ?: return@constrain emptyList()
            if (path.contains("/server/src/")) {
                listOf(Violation(decl, "Destination is declared in a `:server` module — destinations belong in `:api` or `:client`"))
            } else {
                emptyList()
            }
        }
    }
}
