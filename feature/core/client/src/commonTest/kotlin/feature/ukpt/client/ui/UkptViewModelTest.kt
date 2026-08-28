package feature.ukpt.client.ui

import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.fromFlow
import dev.isaacudy.udytils.state.fromSuspending
import dev.isaacudy.udytils.state.isLoading
import dev.isaacudy.udytils.state.isSuccess
import feature.ukpt.Greeting
import feature.ukpt.client.data.GreetingRepository
import feature.ukpt.client.domain.FlowOfGreetingHistory
import feature.ukpt.client.domain.FlowOfGreetingSummaryImpl
import feature.ukpt.client.domain.FlowOfLatestGreeting
import feature.ukpt.client.domain.GreetingSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

class GreetingRepositoryTest {

    @Test
    fun getGreetingAppendsToHistory() = runTest {
        val repo = GreetingRepository()
        repo.getGreeting()
        val history = repo.flowOfGreetingHistory().first()
        assertEquals(1, history.size)
        assertEquals("Hello", history.first().text)
    }

    @Test
    fun getGreetingUpdatesLatest() = runTest {
        val repo = GreetingRepository()
        assertNull(repo.flowOfLatestGreeting().first())

        repo.getGreeting()
        assertEquals(Greeting(text = "Hello"), repo.flowOfLatestGreeting().first())
    }

    @Test
    fun resetGreetingsClearsList() = runTest {
        val repo = GreetingRepository()
        repo.getGreeting()
        repo.getGreeting()
        assertEquals(2, repo.flowOfGreetingHistory().first().size)

        repo.resetGreetings()
        assertTrue(repo.flowOfGreetingHistory().first().isEmpty())
        assertNull(repo.flowOfLatestGreeting().first())
    }

    @Test
    fun multipleGreetsAccumulate() = runTest {
        val repo = GreetingRepository()
        repo.getGreeting()
        repo.getGreeting()
        repo.getGreeting()

        val history = repo.flowOfGreetingHistory().first()
        assertEquals(3, history.size)
        assertEquals(Greeting(text = "Hello"), repo.flowOfLatestGreeting().first())
    }
}

class AsyncStateLoadTest {

    @Test
    fun fromFlowProducesLoadingThenSuccess() = runTest {
        val summary = GreetingSummary(
            latestGreeting = Greeting(text = "Hello"),
            greetingHistory = listOf(Greeting(text = "Hello")),
        )
        val states = AsyncState.fromFlow(flowOf(summary)).toList()

        assertTrue(states[0].isLoading())
        assertIs<AsyncState.Success<GreetingSummary>>(states[1])
        assertEquals(summary, (states[1] as AsyncState.Success).data)
    }

    @Test
    fun fromFlowCapturesError() = runTest {
        val states = AsyncState.fromFlow(
            flow<GreetingSummary> { throw IllegalStateException("connection failed") }
        ).toList()

        assertTrue(states[0].isLoading())
        assertIs<AsyncState.Error<GreetingSummary>>(states[1])
    }

    @Test
    fun fromSuspendingCapturesError() = runTest {
        val states = AsyncState.fromSuspending<Unit> {
            throw IllegalStateException("network error")
        }.toList()

        assertTrue(states[0].isLoading())
        assertIs<AsyncState.Error<Unit>>(states[1])
    }

    @Test
    fun fromSuspendingRetryViaRelaunch() = runTest {
        var callCount = 0
        val run = {
            AsyncState.fromSuspending<Unit> {
                callCount++
                if (callCount == 1) throw IllegalStateException("first attempt fails")
            }
        }

        val first = run().toList()
        assertIs<AsyncState.Error<Unit>>(first.last())

        val second = run().toList()
        assertTrue(second.last().isSuccess())
        assertEquals(2, callCount)
    }
}
