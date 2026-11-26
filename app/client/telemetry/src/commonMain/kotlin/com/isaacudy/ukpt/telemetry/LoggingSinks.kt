package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.trace.rules.AnalyticsSink
import dev.isaacudy.udytils.trace.rules.MetricsSink

/** Prints each analytics event under the name a vendor sink would send. A project replaces this sink with its vendor's. */
class LoggingAnalyticsSink : AnalyticsSink<AnalyticsEvent> {
    override fun record(event: AnalyticsEvent, context: Map<String, String>) {
        val name = when (event) {
            AnalyticsEvent.UkptScreenViewed -> "ukpt_screen_viewed"
            AnalyticsEvent.GreetClicked -> "greet_clicked"
            AnalyticsEvent.GreetingSent -> "greeting_sent"
            AnalyticsEvent.GreetingFailed -> "greeting_failed"
        }
        println("analytics $name $context")
    }
}

/** Prints each timing under the name and tags a vendor sink would send. A project replaces this sink with its vendor's. */
class LoggingMetricsSink : MetricsSink<Metric> {
    override fun recordTiming(metric: Metric, durationNanos: Long) {
        val (name, tags) = when (metric) {
            is Metric.GreetDuration -> "greet.duration" to mapOf("succeeded" to metric.succeeded.toString())
            Metric.GreetingSummaryLoad -> "greeting_summary.load" to emptyMap()
            Metric.UkptScreenLoaded -> "ukpt_screen.loaded" to emptyMap()
        }
        println("metric $name ${durationNanos / 1_000_000.0} ms $tags")
    }
}
