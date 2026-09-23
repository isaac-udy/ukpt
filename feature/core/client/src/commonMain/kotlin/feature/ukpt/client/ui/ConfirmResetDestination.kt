package feature.ukpt.client.ui

import dev.enro.NavigationKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// No @NavigationPath: a cold load resolves a path to a single-entry backstack, which would show
// this dialog with no screen beneath it. While it is open the address bar keeps the path of the
// screen that opened it.
@Serializable
@SerialName("NavigationKey.ConfirmResetDestination")
data object ConfirmResetDestination : NavigationKey
