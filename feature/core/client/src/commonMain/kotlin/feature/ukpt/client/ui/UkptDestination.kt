package feature.ukpt.client.ui

import dev.enro.NavigationKey
import dev.enro.annotations.NavigationPath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@NavigationPath("/")
@SerialName("NavigationKey.UkptDestination")
object UkptDestination : NavigationKey
