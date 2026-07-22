package feature.ukpt.ui

import androidx.lifecycle.ViewModel
import dev.enro.navigationHandle
import dev.isaacudy.udytils.state.ViewModelState
import dev.isaacudy.udytils.state.viewModelState

class UkptViewModel : ViewModel() {

    private val navigation by navigationHandle<UkptDestination>()

    val state: ViewModelState<UkptState> = viewModelState(UkptState())

    fun onGreetClicked() {
        state.update { copy(greetings = greetings + 1) }
    }
}
