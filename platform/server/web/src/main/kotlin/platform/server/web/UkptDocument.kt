package platform.server.web

import dev.isaacudy.udytils.htmx.alpineScript
import dev.isaacudy.udytils.htmx.htmxConfig
import dev.isaacudy.udytils.htmx.htmxScripts
import kotlinx.html.BODY
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.role
import kotlinx.html.script
import kotlinx.html.title

/**
 * What the document around every page needs: its title, and the page's own stylesheets and
 * script files, all served from this origin.
 */
data class DocumentState(
    val title: String,
    val stylesheets: List<String> = emptyList(),
    val scripts: List<String> = emptyList(),
)

/**
 * The document every page renders into, and the only code that renders `<head>` and `<body>`.
 * The page's scripts run before Alpine starts, so the components they register on `alpine:init`
 * exist when it does.
 */
fun HTML.ukptDocument(state: DocumentState, content: BODY.() -> Unit) {
    lang = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title(state.title)
        link(rel = "icon", type = "image/svg+xml", href = "/static/platform/favicon.svg")
        htmxConfig()
        link(rel = "stylesheet", href = "/static/platform/css/tokens.css")
        link(rel = "stylesheet", href = "/static/platform/css/base.css")
        state.stylesheets.forEach { link(rel = "stylesheet", href = it) }
        htmxScripts()
        deferredScript("/static/platform/js/app.js")
        state.scripts.forEach { deferredScript(it) }
        alpineScript()
    }
    body {
        div("app-error") {
            id = APP_ERROR_ID
            role = "alert"
            attributes["hidden"] = ""
        }
        content()
    }
}

/** The element `app.js` fills when an htmx request fails; the document renders it empty and hidden. */
const val APP_ERROR_ID: String = "app-error"

private fun HEAD.deferredScript(src: String) {
    script(src = src) { defer = true }
}
