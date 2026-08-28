package feature.ukpt.client.ui

import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.fromFlow
import dev.isaacudy.udytils.state.fromSuspending
import dev.isaacudy.udytils.state.isLoading
import dev.isaacudy.udytils.state.isSuccess
import feature.ukpt.Greeting
import feature.ukpt.client.domain.GreetingSummary
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
