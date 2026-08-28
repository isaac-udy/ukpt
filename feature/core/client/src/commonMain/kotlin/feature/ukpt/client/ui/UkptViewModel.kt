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
import feature.ukpt.client.domain.GetGreeting
import feature.ukpt.client.domain.ResetGreetings

class UkptViewModel(
    private val flowOfGreetingSummary: FlowOfGreetingSummary,
    private val getGreeting: GetGreeting,
    private val resetGreetings: ResetGreetings,
) : ViewModel() {

    private val navigation by navigationHandle<UkptDestination>()
    private val jobManager = JobManager(viewModelScope)

    val state: ViewModelState<UkptState> = viewModelState(UkptState())

    private val confirmResetResult by registerForNavigationResult(
        onCompleted = {
            jobManager.launchReplacing(RESET_ACTION) {
                resetGreetings()
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
            AsyncState.fromSuspending<Unit> { getGreeting() }
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
