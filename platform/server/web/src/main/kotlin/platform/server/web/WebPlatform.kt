package platform.server.web

import dev.isaacudy.udytils.htmx.htmxAssets
import dev.isaacudy.udytils.htmx.isHtmx
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.HttpStatusCodeContent
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.accept
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.p

/**
 * Installs what every page relies on: the security headers, server-sent events, HTML error pages,
 * the static assets, and the routes of every feature.
 */
fun Application.installWebPlatform(
    routes: List<WebRoutes>,
    contentSecurityPolicy: ContentSecurityPolicy = ContentSecurityPolicy(),
) {
    install(SecurityHeaders) { policy = contentSecurityPolicy }
    install(SSE)
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { status ->
            // A route that answers 404 with its own body keeps it; only a bare status gets the page.
            if (content is HttpStatusCodeContent) {
                call.respondError(status, "Page not found", "There is nothing at this address.")
            }
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception for ${call.request.local.uri}", cause)
            call.respondError(HttpStatusCode.InternalServerError, "Something went wrong", "The request could not be completed.")
        }
    }
    routing {
        webAssets()
        cspReports()
        routes.forEach { with(it) { install() } }
    }
}

/**
 * Scripts and styles load only from this origin, which is why no page may carry inline script or
 * a CDN URL. `style-src` allows no inline `<style>` either; htmx's indicator styles are turned off
 * in the layout's config for that reason.
 *
 * A project may add origins for images, for `fetch` traffic and for form submissions, never for
 * scripts or styles. Browsers apply `form-action` to the redirect that answers a form post too, so
 * a form handled here that redirects to another site needs that site in [formActionSources].
 * Browsers post each violation to [CSP_REPORT_PATH], which logs it; [reportOnly] reports
 * violations without enforcing the policy, so a policy can be tried against real traffic first.
 */
data class ContentSecurityPolicy(
    val imageSources: List<String> = emptyList(),
    val connectSources: List<String> = emptyList(),
    val formActionSources: List<String> = emptyList(),
    val reportOnly: Boolean = false,
) {
    internal val headerName: String
        get() = if (reportOnly) "Content-Security-Policy-Report-Only" else "Content-Security-Policy"

    internal val headerValue: String
        get() = "default-src 'self'; script-src 'self'; style-src 'self'; " +
            "img-src ${sources("'self' data:", imageSources)}; connect-src ${sources("'self'", connectSources)}; " +
            "base-uri 'self'; form-action ${sources("'self'", formActionSources)}; frame-ancestors 'none'; " +
            "report-uri $CSP_REPORT_PATH"

    private fun sources(base: String, extra: List<String>) = (listOf(base) + extra).joinToString(" ")
}

private class SecurityHeadersConfig {
    var policy = ContentSecurityPolicy()
}

private val SecurityHeaders = createApplicationPlugin("SecurityHeaders", ::SecurityHeadersConfig) {
    val name = pluginConfig.policy.headerName
    val value = pluginConfig.policy.headerValue
    onCall { call ->
        call.response.headers.append(name, value)
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Referrer-Policy", "same-origin")
    }
}

/**
 * `/assets/htmx` serves the versioned htmx and Alpine builds; `/static` serves every module's
 * `static/` resources, which each feature keeps under its own directory name. A third-party file
 * sits in a `vendor/` directory with its version in its name, so browsers keep it for a year;
 * everything else is revalidated on every load.
 */
private fun Route.webAssets() {
    htmxAssets()
    staticResources("/static", "static") {
        cacheControl { resource ->
            if (isVendored(resource.path)) listOf(CacheControl.MaxAge(maxAgeSeconds = 31_536_000, visibility = CacheControl.Visibility.Public))
            else listOf(CacheControl.NoCache(null))
        }
    }
}

internal fun isVendored(resourcePath: String): Boolean = "/vendor/" in resourcePath.substringAfterLast("/static/")

/**
 * Only a page navigation gets a full page. htmx does not swap a failed response, so an htmx
 * request gets a short text body that `app.js` shows in the layout's error region, and a script's
 * `fetch` gets the same text to show as it chooses.
 */
private suspend fun ApplicationCall.respondError(status: HttpStatusCode, title: String, message: String) {
    if (!isNavigation) {
        respondText(message, ContentType.Text.Plain, status)
        return
    }
    respondHtml(status) {
        ukptLayout(LayoutState(title)) {
            h1 { +title }
            p { +message }
            p { a(href = "/") { +"Go to the start page" } }
        }
    }
}

/** Browsers say so in `Sec-Fetch-Mode`; a client that doesn't send it is navigating if it asks for HTML. */
private val ApplicationCall.isNavigation: Boolean
    get() {
        if (isHtmx) return false
        val mode = request.headers["Sec-Fetch-Mode"] ?: return request.accept().orEmpty().contains("text/html")
        return mode == "navigate"
    }
