package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.enro.onScreen
import dev.isaacudy.udytils.trace.rules.Trigger
import dev.isaacudy.udytils.trace.rules.analyticsRules
import dev.isaacudy.udytils.trace.rules.metricRules
import dev.isaacudy.udytils.trace.rules.on
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.Greet
import feature.ukpt.client.ui.UkptDestination
import feature.ukpt.client.ui.UkptViewModel

val clientAnalyticsRules = analyticsRules<AnalyticsEvent> {
    event(onScreen(UkptDestination::class), Trigger.Start) { AnalyticsEvent.UkptScreenViewed }
    event(on(UkptViewModel::onGreetClicked), Trigger.Start) { AnalyticsEvent.GreetClicked }
    event(on(Greet::invoke)) { span ->
        when (span.outcome) {
            Outcome.Returned -> AnalyticsEvent.GreetingSent
            is Outcome.Threw -> AnalyticsEvent.GreetingFailed
            else -> null
        }
    }
}

val clientMetricRules = metricRules<Metric> {
    timing(on(Greet::invoke)) { span -> Metric.GreetDuration(succeeded = span.outcome == Outcome.Returned) }
    firstValue(on(FlowOfGreetingSummary::invoke)) { Metric.GreetingSummaryLoad }
}
