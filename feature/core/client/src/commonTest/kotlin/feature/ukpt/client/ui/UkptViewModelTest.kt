@file:OptIn(ExperimentalCoroutinesApi::class)

package feature.ukpt.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.enro.result.NavigationResultChannel
import dev.enro.test.assertOpened
import dev.enro.test.putNavigationHandleForViewModel
import dev.enro.test.runEnroTest
import dev.enro.test.sendClosedForTest
import dev.enro.test.sendCompletedForTest
import dev.isaacudy.udytils.state.AsyncState
import feature.ukpt.Greeting
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.GetGreeting
import feature.ukpt.client.domain.GreetingSummary
import feature.ukpt.client.domain.ResetGreetings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UkptViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val createdViewModels = mutableListOf<ViewModel>()

    private fun <T : ViewModel> T.track(): T {
        createdViewModels += this
        return this
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        // Cancel each ViewModel's scope before resetting Main: a collector left subscribed
        // to pendingResults dispatches to a Main dispatcher that no longer exists on targets
        // without a default one, failing an unrelated test.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        NavigationResultChannel.pendingResults.value = emptyMap()
        Dispatchers.resetMain()
    }

    @Test
    fun loadLifecycleReachesSuccessOnEmission() = runEnroTest {
        val summaryFlow = MutableStateFlow(
            GreetingSummary(
                latestGreeting = Greeting(text = "Hello"),
                greetingHistory = listOf(Greeting(text = "Hello")),
            )
        )
        putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            getGreeting = GetGreeting { "Hello" },
            resetGreetings = ResetGreetings { },
        ).track()

        val state = vm.state.value
        assertIs<AsyncState.Success<GreetingSummary>>(state.greetingSummary)
        assertEquals("Hello", state.greetingSummary.data.latestGreeting?.text)
        assertEquals(1, state.greetingSummary.data.greetingHistory.size)
    }

    @Test
    fun loadErrorLandsInAsyncStateError() = runEnroTest {
        var shouldFail = true
        val summaryFlow = MutableStateFlow(
            GreetingSummary(
                latestGreeting = null,
                greetingHistory = emptyList(),
            )
        )

        putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary {
                if (shouldFail) {
                    flow { throw IllegalStateException("load failed") }
                } else {
                    summaryFlow
                }
            },
            getGreeting = GetGreeting { "Hello" },
            resetGreetings = ResetGreetings { },
        ).track()

        assertIs<AsyncState.Error<GreetingSummary>>(vm.state.value.greetingSummary)

        shouldFail = false
        vm.onRetryClicked()

        assertIs<AsyncState.Success<GreetingSummary>>(vm.state.value.greetingSummary)
    }

    @Test
    fun greetActionDrivesStateToSuccess() = runEnroTest {
        val summaryFlow = MutableStateFlow(
            GreetingSummary(latestGreeting = null, greetingHistory = emptyList())
        )
        var greetCalled = false

        putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            getGreeting = GetGreeting {
                greetCalled = true
                "Hello"
            },
            resetGreetings = ResetGreetings { },
        ).track()

        vm.onGreetClicked()

        assertTrue(greetCalled)
        assertIs<AsyncState.Success<Unit>>(vm.state.value.greetAction)
    }

    @Test
    fun greetActionErrorLandsInAsyncStateError() = runEnroTest {
        val summaryFlow = MutableStateFlow(
            GreetingSummary(latestGreeting = null, greetingHistory = emptyList())
        )

        putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            getGreeting = GetGreeting { throw IllegalStateException("greet failed") },
            resetGreetings = ResetGreetings { },
        ).track()

        vm.onGreetClicked()

        assertIs<AsyncState.Error<Unit>>(vm.state.value.greetAction)
    }

    @Test
    fun resetViaDialogCompletionTriggersResetGreetings() = runEnroTest {
        val summaryFlow = MutableStateFlow(
            GreetingSummary(latestGreeting = null, greetingHistory = emptyList())
        )
        var resetCalled = false

        val handle = putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            getGreeting = GetGreeting { "Hello" },
            resetGreetings = ResetGreetings { resetCalled = true },
        ).track()

        vm.onResetRequested()
        val child = handle.assertOpened<ConfirmResetDestination>()
        child.sendCompletedForTest()

        assertTrue(resetCalled)
    }

    @Test
    fun resetDialogClosedDoesNotTriggerResetGreetings() = runEnroTest {
        val summaryFlow = MutableStateFlow(
            GreetingSummary(latestGreeting = null, greetingHistory = emptyList())
        )
        var resetCalled = false

        val handle = putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            getGreeting = GetGreeting { "Hello" },
            resetGreetings = ResetGreetings { resetCalled = true },
        ).track()

        vm.onResetRequested()
        val child = handle.assertOpened<ConfirmResetDestination>()
        child.sendClosedForTest()

        // Absence is the assertion: sendClosedForTest must not invoke resetGreetings.
        assertTrue(!resetCalled)
    }
}
