package platform.server.web

import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.main

/** A page's title and its own script files, which run before Alpine starts. */
data class LayoutState(
    val title: String,
    val scripts: List<String> = emptyList(),
)

/** The default layout: the page's content in the document's `main` region. */
fun HTML.ukptLayout(state: LayoutState, content: MAIN.() -> Unit) {
    ukptDocument(DocumentState(state.title, scripts = state.scripts)) {
        main("page") { content() }
    }
}
