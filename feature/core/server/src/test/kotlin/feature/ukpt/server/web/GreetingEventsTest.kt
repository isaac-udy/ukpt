package feature.ukpt.server.web

import feature.ukpt.Greeting
import feature.ukpt.server.domain.FlowOfGreetingSummary
import feature.ukpt.server.domain.GreetingSummary
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingEventsTest {

    private val ada = Greeting(id = 1, text = "Hello, Ada")
    private val grace = Greeting(id = 2, text = "Hello, Grace")

    @Test
    fun `the first event replaces the list, later events append and remove`() = runTest {
        val summaries = flowOf(
            GreetingSummary(listOf(ada)),
            GreetingSummary(listOf(ada, grace)),
            GreetingSummary(listOf(grace)),
        )

        val events = greetingEvents(FlowOfGreetingSummary { summaries }).toList()

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
