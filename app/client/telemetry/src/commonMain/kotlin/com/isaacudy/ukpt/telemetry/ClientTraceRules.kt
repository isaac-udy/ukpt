package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.enro.screen
import dev.isaacudy.udytils.trace.rules.Trigger
import dev.isaacudy.udytils.trace.rules.construct
import dev.isaacudy.udytils.trace.rules.on
import dev.isaacudy.udytils.trace.rules.traceRules
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.Greet
import feature.ukpt.client.ui.UkptDestination
import feature.ukpt.client.ui.UkptViewModel

val clientTraceRules = traceRules<AnalyticsEvent> {
    event(screen<UkptDestination>(), Trigger.Start) { AnalyticsEvent.UkptScreenViewed }
    event(on(UkptViewModel::onGreetClicked), Trigger.Start) { AnalyticsEvent.GreetClicked }
    event(on(Greet::invoke)) { span ->
        when (span.outcome) {
            Outcome.Returned -> AnalyticsEvent.GreetingSent
            is Outcome.Threw -> AnalyticsEvent.GreetingFailed
            else -> null
        }
    }
    timing(construct(Constructs.ClientData.Repository))
    firstValue(on(FlowOfGreetingSummary::invoke))
}
