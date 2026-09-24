package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import architecture.rules.shared.isDomainInterfaceOnSide
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

@Describe("""
    A piece of markup: a top-level function whose receiver is a kotlinx.html tag or content type
    other than `HTML` — `FlowContent`, `UL`, `TBODY` — such as `fun FlowContent.greetingForm(…)`.
    Pages compose Components, and a handler that answers an htmx request renders the Component the
    request replaces.

    A Component that renders one item of a live list takes the list's parent tag as its receiver
    (`UL.greetingItem`), so the page and the list's out-of-band updates render the item with the
    same function.
""")
object Component : Construct<ServerWeb>(
    requirements = listOf(
        isFunctionWhere("has a kotlinx.html tag or content receiver other than `HTML`") { fn ->
            val receiver = fn.receiverType?.name ?: return@isFunctionWhere false
            receiver != "HTML" && fn.containingFile.imports.any { it.name == "kotlinx.html.$receiver" }
        },
    ),
) {
    @Describe("A Component must render only the values it is given: no parameter may be a domain interface or a Routes class")
    val rendersValuesOnly by rule {
        rationale(
            """
            kotlinx.html builders do not suspend, so a Component cannot call a domain interface; a
            Component that takes one passes it on to something that runs outside the render. The
            handler reads from the domain and passes the values down.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
            fn.parameters
                .filter { param ->
                    val source = param.type.sourceDeclaration as? KoBaseDeclaration
                    param.type.name.endsWith("Routes") || isDomainInterfaceOnSide(source, "server")
                }
                .map { Violation(fn, "`${fn.name}` takes `${it.type.name}` — pass the values it renders instead") }
        }
    }
}
