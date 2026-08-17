package feature.ukpt.client.ui

import androidx.lifecycle.ViewModel
import dev.enro.navigationHandle
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import dev.isaacudy.udytils.state.ViewModelState
import dev.isaacudy.udytils.state.viewModelState

class UkptViewModel : ViewModel() {

    private val navigation by navigationHandle<UkptDestination>()

    val state: ViewModelState<UkptState> = viewModelState(UkptState())

    private val confirmResetResult by registerForNavigationResult<Boolean> {
        if (it) state.update { copy(greetings = 0) }
    }

    fun onGreetClicked() {
        state.update { copy(greetings = greetings + 1) }
    }

    fun onResetRequested() {
        confirmResetResult.open(ConfirmResetDestination)
    }
}
