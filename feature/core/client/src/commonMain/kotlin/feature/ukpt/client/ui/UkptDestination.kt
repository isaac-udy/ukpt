package feature.ukpt.client.ui

import dev.enro.NavigationKey
import dev.enro.annotations.ExperimentalEnroApi
import dev.enro.annotations.NavigationPath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalEnroApi::class)
@Serializable
@NavigationPath("/")
@SerialName("NavigationKey.UkptDestination")
object UkptDestination : NavigationKey
