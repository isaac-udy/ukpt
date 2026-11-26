package com.isaacudy.ukpt.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Metric {
    @Serializable
    @SerialName("Metric.GreetDuration")
    data class GreetDuration(val succeeded: Boolean) : Metric

    @Serializable
    @SerialName("Metric.GreetingSummaryLoad")
    data object GreetingSummaryLoad : Metric
}
