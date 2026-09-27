package platform.server.web

import dev.isaacudy.udytils.htmx.HtmxAssets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
                    ukptDocument(DocumentState("Shell", stylesheets = listOf("/static/shell/shell.css"), alpine = false)) { nav { +"Menu" } }
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
    fun `a document renders its own body after the error region, with its stylesheets after the platform's, and can leave Alpine out`() = platformTest {
        val document = Jsoup.parse(client.get("/shell").bodyAsText())

        assertEquals(
            listOf("tokens.css", "base.css", "shell.css"),
            document.select("link[rel=stylesheet]").map { it.attr("href").substringAfterLast('/') },
        )
        assertEquals(listOf("div", "nav"), document.body().children().map { it.tagName() })
        assertEquals(APP_ERROR_ID, document.body().child(0).id())
        assertTrue(document.select("script").none { it.attr("src").endsWith(HtmxAssets.ALPINE_CSP) })
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
    fun `the policy sends violations to the report endpoint, which accepts them`() = platformTest {
        val response = client.get("/hello")
        val policy = response.headers["Content-Security-Policy"].orEmpty()
        assertTrue(policy.endsWith("; report-uri /csp-report"), policy)

        val report = client.post(CSP_REPORT_PATH) {
            contentType(ContentType.parse("application/csp-report"))
            setBody("""{"csp-report":{"effective-directive":"script-src-elem","blocked-uri":"inline"}}""")
        }
        assertEquals(HttpStatusCode.NoContent, report.status)
        val junk = client.post(CSP_REPORT_PATH) { setBody("x".repeat(100_000)) }
        assertEquals(HttpStatusCode.NoContent, junk.status)
    }

    @Test
    fun `a report is read, and logged URLs stop at their path`() {
        val report = parseCspReport(
            """{"csp-report":{"document-uri":"https://site.example/login?returnTo=%2Fsecret","violated-directive":"script-src-elem",
               "effective-directive":"script-src-elem","blocked-uri":"inline","source-file":"https://site.example/login","line-number":12,
               "disposition":"report"}}""",
        )
        assertEquals(
            CspViolation("script-src-elem", "inline", "https://site.example/login", "https://site.example/login", 12, "report"),
            report,
        )

        val image = parseCspReport(
            """{"csp-report":{"document-uri":"https://site.example/a#x","violated-directive":"img-src",
               "blocked-uri":"https://images.example/p.png?sig=abc","disposition":"enforce"}}""",
        )
        assertEquals(
            "CSP violation (enforce): img-src blocked https://images.example/p.png on https://site.example/a",
            image?.describe(),
        )

        assertEquals(null, parseCspReport("not json"))
        assertEquals(null, parseCspReport("""{"something":"else"}"""))
    }

    @Test
    fun `platform and htmx assets are served`() = platformTest {
        assertEquals(HttpStatusCode.OK, client.get("/static/platform/css/base.css").status)
        assertEquals(HttpStatusCode.OK, client.get("${HtmxAssets.DEFAULT_PATH}/${HtmxAssets.HTMX}").status)
    }

    @Test
    fun `vendored files are kept for a year and the project's own are revalidated`() = platformTest {
        assertEquals("no-cache", client.get("/static/platform/css/base.css").headers["Cache-Control"])
        assertEquals("max-age=31536000, public", client.get("/static/test/vendor/lib-1.0.0.js").headers["Cache-Control"])
        assertTrue(isVendored("jar:file:/srv/app.jar!/static/analytics/vendor/lib-1.2.3.js"))
        assertTrue(!isVendored("/home/vendor/app/build/resources/main/static/app/js/app.js"))
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
