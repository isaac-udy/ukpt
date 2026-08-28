package feature.ukpt.client.ui

import feature.ukpt.Greeting
import feature.ukpt.client.domain.FlowOfGreetingHistory
import feature.ukpt.client.domain.FlowOfGreetingSummaryImpl
import feature.ukpt.client.domain.FlowOfLatestGreeting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowOfGreetingSummaryImplTest {

    @Test
    fun combinesLatestGreetingAndHistory() = runTest {
        val impl = FlowOfGreetingSummaryImpl(
            flowOfLatestGreeting = FlowOfLatestGreeting { flowOf(Greeting(text = "Hello")) },
            flowOfGreetingHistory = FlowOfGreetingHistory {
                flowOf(listOf(Greeting(text = "Hello"), Greeting(text = "Hi")))
            },
        )

        val result = impl().first()
        assertEquals(Greeting(text = "Hello"), result.latestGreeting)
        assertEquals(2, result.greetingHistory.size)
    }

    @Test
    fun handlesNullLatestGreeting() = runTest {
        val impl = FlowOfGreetingSummaryImpl(
            flowOfLatestGreeting = FlowOfLatestGreeting { flowOf(null) },
            flowOfGreetingHistory = FlowOfGreetingHistory { flowOf(emptyList()) },
        )

        val result = impl().first()
        assertNull(result.latestGreeting)
        assertTrue(result.greetingHistory.isEmpty())
    }

    @Test
    fun updatesWhenSourceChanges() = runTest {
        val latest = MutableStateFlow<Greeting?>(null)
        val history = MutableStateFlow<List<Greeting>>(emptyList())
        val impl = FlowOfGreetingSummaryImpl(
            flowOfLatestGreeting = FlowOfLatestGreeting { latest },
            flowOfGreetingHistory = FlowOfGreetingHistory { history },
        )

        val result1 = impl().first()
        assertNull(result1.latestGreeting)
        assertTrue(result1.greetingHistory.isEmpty())

        latest.value = Greeting(text = "Hello")
        history.value = listOf(Greeting(text = "Hello"))

        val result2 = impl().first()
        assertEquals(Greeting(text = "Hello"), result2.latestGreeting)
        assertEquals(1, result2.greetingHistory.size)
    }
}
