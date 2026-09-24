package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration

@Describe("""
    The names the layer's markup and handlers share: an `object [Name]Paths` holding the URL paths
    of its routes and static files, and an `object [Name]Ids` holding the `ElementId`s that
    `hx-target`, `sse-swap` and out-of-band swaps address, and its event names.

    A path or id written once here is the same string in the route that serves it and the
    attribute that requests it.
""")
object WebConstants : Construct<ServerWeb>(
    requirements = listOf(
        isObjectWhere("is named `[Name]Paths` or `[Name]Ids`") { it.name.endsWith("Paths") || it.name.endsWith("Ids") },
    ),
) {
    @Describe("A Web Constants object must hold no `var`")
    val noVariables by rule {
        constrain { decl, _ ->
            val obj = decl as? KoObjectDeclaration ?: return@constrain emptyList()
            obj.properties().filter { it.isVar }.map { Violation(it, "`${obj.name}.${it.name}` is a `var`") }
        }
    }
}
