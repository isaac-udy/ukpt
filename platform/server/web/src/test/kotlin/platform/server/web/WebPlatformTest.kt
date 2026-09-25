package platform.server.web

import dev.isaacudy.udytils.htmx.HtmxAssets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.html.h1
import kotlinx.html.nav
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebPlatformTest {

    private val routes = object : WebRoutes {
        override fun Route.install() {
            get("/hello") { call.respondHtml { ukptLayout(LayoutState("Hello", scripts = listOf("/static/hello/hello.js"))) { h1 { +"Hello" } } } }
            get("/shell") {
                call.respondHtml {
                    ukptDocument(DocumentState("Shell", stylesheets = listOf("/static/shell/shell.css"))) { nav { +"Menu" } }
                }
            }
            get("/boom") { error("boom") }
            get("/gone") { call.respondText("That item was withdrawn.", status = HttpStatusCode.NotFound) }
        }
    }

    private fun platformTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { installWebPlatform(listOf(routes)) }
        block()
    }

    @Test
    fun `pages load scripts from this origin in order, alpine last`() = platformTest {
        val response = client.get("/hello")
        val document = Jsoup.parse(response.bodyAsText())

        assertTrue("script-src 'self'" in response.headers["Content-Security-Policy"].orEmpty())
        assertEquals(
            listOf(HtmxAssets.HTMX, HtmxAssets.SSE_EXTENSION, "app.js", "hello.js", HtmxAssets.ALPINE_CSP),
            document.select("script").map { it.attr("src").substringAfterLast('/') },
        )
        assertTrue(document.select("script").all { it.data().isEmpty() && it.hasAttr("defer") })
    }

    @Test
    fun `a document renders its own body after the error region, with its stylesheets after the platform's`() = platformTest {
        val document = Jsoup.parse(client.get("/shell").bodyAsText())

        assertEquals(
            listOf("tokens.css", "base.css", "shell.css"),
            document.select("link[rel=stylesheet]").map { it.attr("href").substringAfterLast('/') },
        )
        assertEquals(listOf("div", "nav"), document.body().children().map { it.tagName() })
        assertEquals(APP_ERROR_ID, document.body().child(0).id())
    }

    @Test
    fun `a project adds image, connect and form-action origins, or only reports violations`() = testApplication {
        application {
            installWebPlatform(
                listOf(routes),
                ContentSecurityPolicy(
                    imageSources = listOf("https://images.example"),
                    connectSources = listOf("https://analytics.example"),
                    formActionSources = listOf("https://legacy.example"),
                    reportOnly = true,
                ),
            )
        }
        val response = client.get("/hello")

        assertEquals(null, response.headers["Content-Security-Policy"])
        val policy = response.headers["Content-Security-Policy-Report-Only"].orEmpty()
        assertTrue("img-src 'self' data: https://images.example;" in policy, policy)
        assertTrue("connect-src 'self' https://analytics.example;" in policy, policy)
        assertTrue("form-action 'self' https://legacy.example;" in policy, policy)
        assertTrue("script-src 'self';" in policy, policy)
    }

    @Test
    fun `platform and htmx assets are served`() = platformTest {
        assertEquals(HttpStatusCode.OK, client.get("/static/platform/css/base.css").status)
        assertEquals(HttpStatusCode.OK, client.get("${HtmxAssets.DEFAULT_PATH}/${HtmxAssets.HTMX}").status)
    }

    @Test
    fun `errors render a page, or text for htmx`() = platformTest {
        val page = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, page.status)
        assertEquals("Something went wrong", Jsoup.parse(page.bodyAsText()).select("h1").text())

        val fragment = client.get("/missing") { header("HX-Request", "true") }
        assertEquals(HttpStatusCode.NotFound, fragment.status)
        assertEquals("There is nothing at this address.", fragment.bodyAsText())
    }

    @Test
    fun `a route's own not-found body is kept`() = platformTest {
        val response = client.get("/gone")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("That item was withdrawn.", response.bodyAsText())

        val page = client.get("/missing")
        assertEquals("Page not found", Jsoup.parse(page.bodyAsText()).select("h1").text())
    }
}
