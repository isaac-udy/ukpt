package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

@Describe("""
    Markup that several [Pages](#page) render inside, such as an app shell with navigation: a
    top-level `fun HTML.[name]Layout(state: [Name]LayoutState, content: FlowContent.() -> Unit)`.
    Its [View State](#view-state) carries everything the shell shows, and `content` renders the
    Page's own part.

    A Layout renders through the platform: `ukptLayout` puts the page in the document's `main`
    region, and `ukptDocument` hands a shell that draws its own regions the document's `body`.
    Layouts nest, and every chain ends at the platform, which alone renders `<head>` and `<body>`.
""")
object Layout : Construct<ServerWeb>(
    requirements = listOf(
        isFunctionWhere("has an `HTML` receiver and is named `[name]Layout`") { it.receiverType?.name == "HTML" && it.name.endsWith("Layout") },
    ),
) {
    @Describe("A Layout must take exactly two parameters: its View State, then the content it renders around")
    val takesItsViewStateAndContent by rule {
        rationale("Everything a Layout shows is in its View State, so a snapshot test of a Page covers every value its shell can put on it.")
        constrain { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
            val parameters = fn.parameters
            val stateThenContent = parameters.size == 2 &&
                parameters[0].type.name.substringBefore('<').endsWith("State") &&
                parameters[1].type.name.contains("->")
            if (stateThenContent) {
                emptyList()
            } else {
                listOf(Violation(fn, "`${fn.name}` must take a `[Name]State` and a content lambda, found (${parameters.joinToString { it.type.name }})"))
            }
        }
    }

    @Describe("A Layout must render through another Layout or the platform's document")
    val rendersThroughThePlatform by rule {
        rationale("The platform's document carries the htmx configuration, the scripts in the order Alpine needs, and the error region the platform script fills.")
        note("Tested on the function body: a call to a function whose name ends in `Layout` or `Document`, other than the Layout itself.")
        constrain { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
            val renders = layoutOrDocumentCall.findAll(fn.text).any { it.groupValues[1] != fn.name }
            if (renders) emptyList() else listOf(Violation(fn, "`${fn.name}` does not render through a `…Layout(…)` or `…Document(…)` function"))
        }
    }
}

private val layoutOrDocumentCall = Regex("""\b(\w+(?:Layout|Document))\s*\(""")
