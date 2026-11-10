package platform.testing.subcutaneous

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.server.host.installServerHost
import platform.server.web.WebRoutes
import platform.testing.AdapterTest
import platform.testing.robots.Locator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@AdapterTest
class HtmxTabWebSocketTest {

    private val frames = Channel<String>(Channel.UNLIMITED)
    private val openSockets = AtomicInteger()

    private val liveRoutes = object : WebRoutes {
        override fun Route.install() {
            get("/live") {
                call.response.cookies.append("session", "ada")
                call.respondText(page(liveRegion()), ContentType.Text.Html)
            }
            get("/live/off") { call.respondText("""<div id="live">Paused</div>""", ContentType.Text.Html) }
            get("/live/on") { call.respondText(liveRegion(), ContentType.Text.Html) }
            get("/unextended") { call.respondText(page("""<div ws-connect="/live/socket"><p id="who">Connecting</p></div>"""), ContentType.Text.Html) }
            get("/sender") {
                call.respondText(page("""<div hx-ext="ws" ws-connect="/live/socket"><form ws-send><button id="send">Send</button></form></div>"""), ContentType.Text.Html)
            }
            webSocket("/live/socket") {
                openSockets.incrementAndGet()
                send(Frame.Text("""<p id="who">${call.request.cookies["session"]}</p>"""))
                val forwarding = launch { for (frame in frames) send(Frame.Text(frame)) }
                try {
                    for (ignored in incoming) Unit
                } finally {
                    forwarding.cancel()
                    openSockets.decrementAndGet()
                }
            }
        }
    }

    private fun liveTest(block: suspend ApplicationTestBuilder.(HtmxTab) -> Unit) = testApplication {
        application { installServerHost(listOf(module { single { liveRoutes } bind WebRoutes::class })) }
        startApplication()
        withContext(Dispatchers.IO) { HtmxTab(client.engine, scripts = true).use { tab -> block(tab) } }
    }

    @Test
    fun `frames swap elements by id, or as hx-swap-oob says, over a socket carrying the tab's cookies`() = liveTest { tab ->
        tab.open("/live")
        awaitTexts(tab, "#who", listOf("ada"))

        frames.send(
            """
            <p id="status" hx-swap-oob="innerHTML">Selling <b>lot 3</b></p>
            <div hx-swap-oob="beforeend:#lots"><li>Lot 2</li></div>
            <p id="notice" hx-swap-oob="delete"></p>
            """.trimIndent(),
        )

        awaitTexts(tab, "#status", listOf("Selling lot 3"))
        awaitTexts(tab, "#lots li", listOf("Lot 1", "Lot 2"))
        awaitTexts(tab, "#notice", emptyList())
    }

    @Test
    fun `a socket closes when its element leaves the page, and one swapped in opens`() = liveTest { tab ->
        tab.open("/live")
        awaitTexts(tab, "#who", listOf("ada"))

        tab.click(Locator.css("#pause"))
        awaitTexts(tab, "#live", listOf("Paused"))
        awaitOpenSockets(0)

        tab.click(Locator.css("#resume"))
        awaitTexts(tab, "#who", listOf("ada"))
        awaitOpenSockets(1)
    }

    @Test
    fun `ws-connect without the ws extension stays closed, and the page reports it`() = liveTest { tab ->
        tab.open("/unextended")

        assertEquals(listOf("Connecting"), tab.texts(Locator.css("#who")))
        assertEquals(0, openSockets.get())
        assertTrue("hx-ext=\"ws\"" in tab.errorText().orEmpty(), tab.errorText())
    }

    @Test
    fun `ws-send fails the step that reaches it`() = liveTest { tab ->
        tab.open("/sender")

        val failure = assertFailsWith<AssertionError> { tab.click(Locator.css("#send")) }
        assertTrue("ws-send" in failure.message.orEmpty(), failure.message)
    }

    private fun awaitOpenSockets(count: Int) = awaitUntil("$count open sockets; there are ${openSockets.get()}") { openSockets.get() == count }

    private fun awaitTexts(tab: HtmxTab, css: String, texts: List<String>) =
        awaitUntil("$css to show $texts; it shows ${tab.texts(Locator.css(css))}, error ${tab.errorText()}") { tab.texts(Locator.css(css)) == texts }

    private fun awaitUntil(expectation: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("Expected $expectation")
            Thread.sleep(20)
        }
    }
}

private fun liveRegion() = """
    <div id="live" hx-ext="ws" ws-connect="/live/socket">
      <p id="who">Connecting</p>
      <p id="status">Waiting</p>
      <ul id="lots"><li>Lot 1</li></ul>
      <p id="notice">The sale starts soon</p>
    </div>
""".trimIndent()

private fun page(body: String) = """
    <!DOCTYPE html>
    <html><body>
    <div id="app-error" hidden></div>
    $body
    <button id="pause" hx-get="/live/off" hx-target="#live" hx-swap="outerHTML">Pause</button>
    <button id="resume" hx-get="/live/on" hx-target="#live" hx-swap="outerHTML">Resume</button>
    </body></html>
""".trimIndent()
