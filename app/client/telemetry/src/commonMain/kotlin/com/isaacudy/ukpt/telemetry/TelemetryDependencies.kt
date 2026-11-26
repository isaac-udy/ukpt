package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.rules.AnalyticsSink
import dev.isaacudy.udytils.trace.rules.MetricsSink
import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Applies [clientAnalyticsRules] and [clientMetricRules] to the spans of the running app until closed. */
class TelemetryRegistration(analytics: AnalyticsSink<AnalyticsEvent>, metrics: MetricsSink<Metric>) : AutoCloseable {
    private val registrations = listOf(
        Trace.addSink(clientAnalyticsRules.sink(TraceSiteTable, analytics)),
        Trace.addSink(clientMetricRules.sink(TraceSiteTable, metrics)),
    )

    override fun close() = registrations.forEach { it.close() }
}

val telemetryDependencies: Module = module {
    singleOf(::LoggingAnalyticsSink) bind AnalyticsSink::class
    singleOf(::LoggingMetricsSink) bind MetricsSink::class
    singleOf(::TelemetryRegistration) { createdAtStart() } onClose { it?.close() }
}
