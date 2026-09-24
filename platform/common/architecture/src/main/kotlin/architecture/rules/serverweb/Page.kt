package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

@Describe("""
    A whole HTML document: a top-level `fun HTML.[name]Page(state: [Name]State)` that renders its
    [View State](#view-state) inside a [Layout](#layout) — the platform's `ukptLayout`, or a shell
    the project's features share. A handler responds with it through
    `call.respondHtml { [name]Page(state) }`.

    A Page composes [Components](#component). A handler that answers an htmx request renders the
    Component it replaces, not the Page.
""")
object Page : Construct<ServerWeb>(
    requirements = listOf(
        isFunctionWhere("has an `HTML` receiver and is named `[name]Page`") { it.receiverType?.name == "HTML" && it.name.endsWith("Page") },
    ),
) {
    @Describe("A Page must take exactly one parameter, its View State")
    val takesItsViewState by rule {
        rationale("Everything a Page shows is in its View State, so a snapshot test of the Page covers every value the handler can put on it.")
        constrain { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
            val parameters = fn.parameters
            if (parameters.size == 1 && parameters.single().type.name.substringBefore('<').endsWith("State")) {
                emptyList()
            } else {
                listOf(Violation(fn, "`${fn.name}` must take one `[Name]State` parameter, found (${parameters.joinToString { it.type.name }})"))
            }
        }
    }

    @Describe("A Page must render through a Layout")
    val rendersThroughLayout by rule {
        rationale("Every Layout ends at the platform's document, which carries the htmx configuration, the scripts in the order Alpine needs, and the error region the platform script fills.")
        note("Tested on the function body: a call to a function whose name ends in `Layout`.")
        constrain { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
            if (layoutCall.containsMatchIn(fn.text)) emptyList() else listOf(Violation(fn, "`${fn.name}` does not render through a `…Layout(…)` function"))
        }
    }
}

private val layoutCall = Regex("""\b\w+Layout\s*\(""")
