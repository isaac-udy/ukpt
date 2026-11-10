package feature.sample.server.web

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import platform.testing.ComponentTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ComponentTest
class SampleEventsTest {

    @Test
    fun `the first event replaces the list, later events append`() = runTest {
        val events = sampleEvents { flowOf(listOf("a"), listOf("a", "b")) }.toList()

        assertEquals(listOf(SampleIds.SAMPLES_EVENT), events.map { it.event }.distinct())
        val swaps = events.map { event ->
            Jsoup.parseBodyFragment(event.data.orEmpty()).select("template > *").map { it.attr("hx-swap-oob") }
        }
        assertEquals(listOf(listOf("innerHTML"), listOf("beforeend:#samples")), swaps)
    }
}
