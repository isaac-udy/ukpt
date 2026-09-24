package platform.server.web

import dev.isaacudy.udytils.htmx.alpineScript
import dev.isaacudy.udytils.htmx.htmxConfig
import dev.isaacudy.udytils.htmx.htmxScripts
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.role
import kotlinx.html.script
import kotlinx.html.title

/**
 * The document every page renders into. [scripts] are the page's own script files; they run
 * before Alpine starts, so the components they register on `alpine:init` exist when it does.
 */
fun HTML.ukptLayout(
    title: String,
    scripts: List<String> = emptyList(),
    content: MAIN.() -> Unit,
) {
    lang = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title(title)
        htmxConfig()
        link(rel = "stylesheet", href = "/static/platform/css/tokens.css")
        link(rel = "stylesheet", href = "/static/platform/css/base.css")
        htmxScripts()
        deferredScript("/static/platform/js/app.js")
        scripts.forEach { deferredScript(it) }
        alpineScript()
    }
    body {
        div("app-error") {
            id = APP_ERROR_ID
            role = "alert"
            attributes["hidden"] = ""
        }
        main("page") { content() }
    }
}

/** The element `app.js` fills when an htmx request fails; the layout renders it empty and hidden. */
const val APP_ERROR_ID: String = "app-error"

private fun HEAD.deferredScript(src: String) {
    script(src = src) { defer = true }
}
