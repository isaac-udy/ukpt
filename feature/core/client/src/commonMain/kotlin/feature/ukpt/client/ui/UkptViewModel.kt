package feature.ukpt.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.enro.navigationHandle
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import dev.isaacudy.udytils.coroutines.JobManager
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.ViewModelState
import dev.isaacudy.udytils.state.fromFlow
import dev.isaacudy.udytils.state.fromSuspending
import dev.isaacudy.udytils.state.viewModelState
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.Greet
import feature.ukpt.client.domain.UpdateGreetings

class UkptViewModel(
    private val flowOfGreetingSummary: FlowOfGreetingSummary,
    private val greet: Greet,
    private val updateGreetings: UpdateGreetings,
) : ViewModel() {

    private val navigation by navigationHandle<UkptDestination>()
    private val jobManager = JobManager(viewModelScope)

    val state: ViewModelState<UkptState> = viewModelState(UkptState())

    private val confirmResetResult by registerForNavigationResult(
        onCompleted = {
            jobManager.launchReplacing(RESET_ACTION) {
                updateGreetings.reset()
            }
        },
    )

    init {
        loadGreetingSummary()
    }

    private fun loadGreetingSummary() {
        jobManager.launchReplacing(LOAD_DATA) {
            AsyncState.fromFlow(flowOfGreetingSummary())
                .collect { state.update { copy(greetingSummary = it) } }
        }
    }

    fun onRetryClicked() {
        loadGreetingSummary()
    }

    fun onGreetClicked() {
        jobManager.launchReplacing(GREET_ACTION) {
            AsyncState.fromSuspending<Unit> { greet() }
                .collect { state.update { copy(greetAction = it) } }
        }
    }

    fun onResetRequested() {
        confirmResetResult.open(ConfirmResetDestination)
    }

    private companion object {
        const val LOAD_DATA = "loadData"
        const val GREET_ACTION = "greetAction"
        const val RESET_ACTION = "resetAction"
    }
}
