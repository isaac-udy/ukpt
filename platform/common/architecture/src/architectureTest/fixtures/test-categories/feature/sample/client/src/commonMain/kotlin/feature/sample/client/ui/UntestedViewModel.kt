package feature.sample.client.ui

import androidx.lifecycle.ViewModel
import dev.enro.navigationHandle
import dev.isaacudy.udytils.state.viewModelState

class UntestedViewModel : ViewModel() {
    private val navigation by navigationHandle<UntestedDestination>()

    val state = viewModelState(UntestedState())
}
