package feature.ukpt.server.web

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import platform.testing.ComponentTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ComponentTest
class GreetingEventsTest {

    private val ada = Greeting(id = "1", text = "Hello, Ada")
    private val grace = Greeting(id = "2", text = "Hello, Grace")

    @Test
    fun `the first event replaces the list, later events append and remove`() = runTest {
        val histories = flowOf(listOf(ada), listOf(ada, grace), listOf(grace))

        val events = greetingEvents { histories }.toList()

        assertEquals(listOf(UkptIds.GREETINGS_EVENT), events.map { it.event }.distinct())
        val swaps = events.map { event ->
            Jsoup.parseBodyFragment(event.data.orEmpty()).select("template > *").map { it.attr("hx-swap-oob") }
        }
        assertEquals(
            listOf(listOf("innerHTML"), listOf("beforeend:#greetings"), listOf("delete")),
            swaps,
        )
    }
}
