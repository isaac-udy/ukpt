package platform.server.web

import dev.isaacudy.udytils.htmx.HtmxAssets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.html.h1
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebPlatformTest {

    private val routes = object : WebRoutes {
        override fun Route.install() {
            get("/hello") { call.respondHtml { ukptLayout("Hello", scripts = listOf("/static/hello/hello.js")) { h1 { +"Hello" } } } }
            get("/boom") { error("boom") }
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
}
