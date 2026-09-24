package feature.ukpt.server.web

import feature.ukpt.server.data.GreetingRepository
import feature.ukpt.server.domain.GreetImpl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.first
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import platform.server.web.installWebPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UkptRoutesTest {

    private val repository = GreetingRepository()
    private val routes = UkptRoutes(
        flowOfGreetingSummary = repository.flowOfGreetingSummary,
        greet = GreetImpl(repository.flowOfGreetingSummary, repository.updateGreetings),
        updateGreetings = repository.updateGreetings,
    )

    private fun routesTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { installWebPlatform(listOf(routes)) }
        block(createClient { followRedirects = false })
    }

    @Test
    fun `the home page renders the form and the list`() = routesTest { client ->
        repository.updateGreetings.add("Hello, Ada")

        val page = client.get(UkptPaths.HOME).document()

        assertEquals("Greetings", page.title())
        assertEquals(1, page.select("form#greeting-form input[name=name]").size)
        assertEquals(listOf("Hello, Ada"), page.select("ul#greetings > li > span").map { it.text() })
        assertEquals(UkptPaths.GREETING_EVENTS, page.select("[sse-connect]").attr("sse-connect"))
    }

    @Test
    fun `an invalid name re-renders only the form for htmx`() = routesTest { client ->
        val response = client.postName("  ", htmx = true)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val fragment = response.document()
        assertEquals("Enter a name.", fragment.select("form#greeting-form .field-error").text())
        assertEquals(0, fragment.select("h1").size)
    }

    @Test
    fun `an invalid name re-renders the whole page without htmx`() = routesTest { client ->
        val response = client.postName("", htmx = false)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val page = response.document()
        assertEquals("Greetings", page.select("h1").text())
        assertEquals("true", page.select("input[name=name]").attr("aria-invalid"))
    }

    @Test
    fun `a valid name greets, then redirects without htmx`() = routesTest { client ->
        val response = client.postName("Ada", htmx = false)

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals(UkptPaths.HOME, response.headers[HttpHeaders.Location])
        assertEquals(listOf("Hello, Ada"), repository.flowOfGreetingSummary().first().greetings.map { it.text })
    }

    @Test
    fun `a valid name greets, then resets the form for htmx`() = routesTest { client ->
        val response = client.postName("Ada", htmx = true)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", response.document().select("input[name=name]").attr("value"))
        assertEquals(1, repository.flowOfGreetingSummary().first().greetings.size)
    }

    @Test
    fun `removing a greeting answers htmx with no content`() = routesTest { client ->
        repository.updateGreetings.add("Hello, Ada")
        val id = repository.flowOfGreetingSummary().first().greetings.single().id

        val response = client.submitForm(UkptPaths.deleteGreeting(id), parameters { }) { header("HX-Request", "true") }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(repository.flowOfGreetingSummary().first().greetings.isEmpty())
    }

    @Test
    fun `the event stream starts with the whole list`() = routesTest { _ ->
        repository.updateGreetings.add("Hello, Ada")
        val client = createClient { install(SSE) }

        var first: String? = null
        client.sse(UkptPaths.GREETING_EVENTS) {
            val event = incoming.first()
            assertEquals(UkptIds.GREETINGS_EVENT, event.event)
            first = event.data
        }

        val template = Jsoup.parseBodyFragment(checkNotNull(first)).select("template > ul#greetings").single()
        assertEquals("innerHTML", template.attr("hx-swap-oob"))
        assertEquals(listOf("Hello, Ada"), template.select("li > span").map { it.text() })
    }

    private suspend fun HttpClient.postName(name: String, htmx: Boolean): HttpResponse =
        submitForm(UkptPaths.GREETINGS, parameters { append("name", name) }) {
            if (htmx) header("HX-Request", "true")
        }

    private suspend fun HttpResponse.document(): Document = Jsoup.parse(bodyAsText())
}
