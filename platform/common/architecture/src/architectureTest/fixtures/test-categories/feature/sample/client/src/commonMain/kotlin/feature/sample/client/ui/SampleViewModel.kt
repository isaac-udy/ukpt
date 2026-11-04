package feature.sample.client.ui

import androidx.lifecycle.ViewModel
import dev.enro.navigationHandle
import dev.isaacudy.udytils.state.viewModelState

class SampleViewModel : ViewModel() {
    private val navigation by navigationHandle<SampleDestination>()

    val state = viewModelState(SampleState())
}
