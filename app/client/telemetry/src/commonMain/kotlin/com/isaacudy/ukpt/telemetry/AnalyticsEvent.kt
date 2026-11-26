package com.isaacudy.ukpt.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AnalyticsEvent {
    @Serializable
    @SerialName("AnalyticsEvent.UkptScreenViewed")
    data object UkptScreenViewed : AnalyticsEvent

    @Serializable
    @SerialName("AnalyticsEvent.GreetClicked")
    data object GreetClicked : AnalyticsEvent

    @Serializable
    @SerialName("AnalyticsEvent.GreetingSent")
    data object GreetingSent : AnalyticsEvent

    @Serializable
    @SerialName("AnalyticsEvent.GreetingFailed")
    data object GreetingFailed : AnalyticsEvent
}
