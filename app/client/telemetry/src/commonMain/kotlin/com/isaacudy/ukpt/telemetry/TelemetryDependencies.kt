package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.rules.AnalyticsSink
import dev.isaacudy.udytils.trace.rules.MetricsSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Applies [clientAnalyticsRules] and [clientMetricRules] to the spans of the running app until closed.
 * The sinks receive events and metrics on [Dispatchers.Default], not on the thread that ended the span.
 */
class TelemetryRegistration(analytics: AnalyticsSink<AnalyticsEvent>, metrics: MetricsSink<Metric>) : AutoCloseable {
    private val delivery = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registrations = listOf(
        Trace.addSink(clientAnalyticsRules.sink(TraceSiteTable, analytics, delivery)),
        Trace.addSink(clientMetricRules.sink(TraceSiteTable, metrics, delivery)),
    )

    override fun close() {
        registrations.forEach { it.close() }
        delivery.cancel()
    }
}

val telemetryDependencies: Module = module {
    singleOf(::LoggingAnalyticsSink) bind AnalyticsSink::class
    singleOf(::LoggingMetricsSink) bind MetricsSink::class
    singleOf(::TelemetryRegistration) { createdAtStart() } onClose { it?.close() }
}
