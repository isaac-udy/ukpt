package architecture.rules.clientui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider

@Describe("""
    A serializable data class or object that represents the navigation contract for a particular
    screen: the input parameters required by that screen (if any) and the output result type
    provided by that screen (if any).

    * **Note:** "Minimal data" means identifiers, not payloads. A Destination should accept a
      `User.Id` and let the Screen load the associated `User`, rather than accepting an entire
      `User`.
""")
object Destination : Construct<ClientUi>(
    requirements = listOf(
        isClassOrObject,
        predicate("implements `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>`") { d ->
            (d is KoClassDeclaration || d is KoObjectDeclaration) &&
                d.parents().any { parent -> parent.name.contains("NavigationKey") }
        },
        hasNameEndingWith("Destination"),
        isAnnotatedWith("Serializable"),
        hasFileNameMatchingDeclaration,
    ),
) {
    @Describe("A Destination should accept the minimal data required to initialise the associated Screen")
    val minimalData by guidance
    @Describe("A Destination lives in the feature's `:client` module, and in `:api` only when another feature navigates to it")
    val definedInApiOrClient by rule {
        rationale(
            """
            `:api` is what features share through — with each other, or across the network. A
            Destination another feature has to name is one of those things; a Destination only its
            own feature names is not, and publishing it widens the feature's surface for nothing.

            App modules are not the test. The shell, the admin client and the server wiring depend
            on the side modules directly and are meant to see and compose every feature's
            declarations, so a reference from `app/…` — a graph binding, a start destination, a
            shell decorator — never makes a Destination `:api`.
            """.trimIndent(),
        )
        note("The test measures the `:server` half: a Destination is client-side, so it is never declared in a `:server` module.")
        note("Which of `:api` and `:client` holds a Destination is a judgement about who navigates to it, so it is read rather than tested; the default is `:client`, and a Destination moves to `:api` when a second feature needs it.")
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
