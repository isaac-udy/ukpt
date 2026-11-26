package com.isaacudy.ukpt.telemetry

import androidx.lifecycle.viewModelScope
import dev.isaacudy.udytils.state.isSuccess
import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.enro.onScreen
import dev.isaacudy.udytils.trace.rules.Trigger
import dev.isaacudy.udytils.trace.rules.analyticsRules
import dev.isaacudy.udytils.trace.rules.metricRules
import dev.isaacudy.udytils.trace.rules.onCalled
import dev.isaacudy.udytils.trace.rules.onConstructed
import dev.isaacudy.udytils.trace.rules.scoped
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.Greet
import feature.ukpt.client.ui.UkptDestination
import feature.ukpt.client.ui.UkptViewModel
import kotlinx.coroutines.flow.first

val clientAnalyticsRules = analyticsRules<AnalyticsEvent> {
    onScreen(UkptDestination::class).event(Trigger.Start) { AnalyticsEvent.UkptScreenViewed }
    onCalled(UkptViewModel::onGreetClicked).event(Trigger.Start) { AnalyticsEvent.GreetClicked }
    onCalled(Greet::invoke).event {
        when (span.outcome) {
            Outcome.Returned -> AnalyticsEvent.GreetingSent
            is Outcome.Threw -> AnalyticsEvent.GreetingFailed
            else -> null
        }
    }
}

val clientMetricRules = metricRules<Metric> {
    onCalled(Greet::invoke).timing { Metric.GreetDuration(succeeded = span.outcome == Outcome.Returned) }
    onCalled(FlowOfGreetingSummary::invoke).firstValue { Metric.GreetingSummaryLoad }
    onConstructed(UkptViewModel::class).scoped { it.viewModelScope }.timing { viewModel ->
        viewModel.state.asStateFlow().first { it.greetingSummary.isSuccess() }
        Metric.UkptScreenLoaded
    }
}
