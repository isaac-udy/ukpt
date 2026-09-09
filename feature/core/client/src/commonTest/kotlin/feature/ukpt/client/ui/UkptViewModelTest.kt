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
import feature.ukpt.client.domain.Greet
import feature.ukpt.client.domain.GreetingSummary
import feature.ukpt.client.domain.UpdateGreetings
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
    private val emptySummary = GreetingSummary(greetings = emptyList())

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
        val summaryFlow = MutableStateFlow(GreetingSummary(greetings = listOf(Greeting(text = "Hello"))))
        putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            greet = Greet { },
            updateGreetings = UpdateGreetings { },
        ).track()

        val state = vm.state.value
        assertIs<AsyncState.Success<GreetingSummary>>(state.greetingSummary)
        assertEquals("Hello", state.greetingSummary.data.latest?.text)
        assertEquals(1, state.greetingSummary.data.greetings.size)
    }

    @Test
    fun loadErrorLandsInAsyncStateError() = runEnroTest {
        var shouldFail = true
        val summaryFlow = MutableStateFlow(emptySummary)

        putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary {
                if (shouldFail) {
                    flow { throw IllegalStateException("load failed") }
                } else {
                    summaryFlow
                }
            },
            greet = Greet { },
            updateGreetings = UpdateGreetings { },
        ).track()

        assertIs<AsyncState.Error<GreetingSummary>>(vm.state.value.greetingSummary)

        shouldFail = false
        vm.onRetryClicked()

        assertIs<AsyncState.Success<GreetingSummary>>(vm.state.value.greetingSummary)
    }

    @Test
    fun greetActionDrivesStateToSuccess() = runEnroTest {
        val summaryFlow = MutableStateFlow(emptySummary)
        var greetCalled = false

        putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            greet = Greet { greetCalled = true },
            updateGreetings = UpdateGreetings { },
        ).track()

        vm.onGreetClicked()

        assertTrue(greetCalled)
        assertIs<AsyncState.Success<Unit>>(vm.state.value.greetAction)
    }

    @Test
    fun greetActionErrorLandsInAsyncStateError() = runEnroTest {
        val summaryFlow = MutableStateFlow(emptySummary)

        putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            greet = Greet { throw IllegalStateException("greet failed") },
            updateGreetings = UpdateGreetings { },
        ).track()

        vm.onGreetClicked()

        assertIs<AsyncState.Error<Unit>>(vm.state.value.greetAction)
    }

    @Test
    fun resetViaDialogCompletionResetsGreetings() = runEnroTest {
        val summaryFlow = MutableStateFlow(emptySummary)
        val updates = mutableListOf<UpdateGreetings.Update>()

        val handle = putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            greet = Greet { },
            updateGreetings = UpdateGreetings { updates += it },
        ).track()

        vm.onResetRequested()
        val child = handle.assertOpened<ConfirmResetDestination>()
        child.sendCompletedForTest()

        assertEquals(listOf<UpdateGreetings.Update>(UpdateGreetings.Update.Reset), updates)
    }

    @Test
    fun resetDialogClosedDoesNotResetGreetings() = runEnroTest {
        val summaryFlow = MutableStateFlow(emptySummary)
        val updates = mutableListOf<UpdateGreetings.Update>()

        val handle = putNavigationHandleForViewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val vm = UkptViewModel(
            flowOfGreetingSummary = FlowOfGreetingSummary { summaryFlow },
            greet = Greet { },
            updateGreetings = UpdateGreetings { updates += it },
        ).track()

        vm.onResetRequested()
        val child = handle.assertOpened<ConfirmResetDestination>()
        child.sendClosedForTest()

        // Absence is the assertion: sendClosedForTest must not reach updateGreetings.
        assertTrue(updates.isEmpty())
    }
}
