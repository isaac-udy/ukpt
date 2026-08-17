package feature.ukpt.client.ui

import androidx.lifecycle.ViewModel
import dev.enro.complete
import dev.enro.navigationHandle
import dev.enro.requestClose

class ConfirmResetViewModel : ViewModel() {

    private val navigation by navigationHandle<ConfirmResetDestination>()

    fun onConfirm() {
        navigation.complete(true)
    }

    fun onDismiss() {
        navigation.requestClose()
    }
}
