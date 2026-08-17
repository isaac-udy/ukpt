package feature.ukpt.client.ui

import dev.enro.NavigationKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("NavigationKey.ConfirmResetDestination")
data object ConfirmResetDestination : NavigationKey.WithResult<Boolean>
