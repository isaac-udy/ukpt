package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.state.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.rules.InMemoryAnalyticsSink
import dev.isaacudy.udytils.trace.rules.InMemoryMetricsSink
import feature.ukpt.client.ui.UkptDestination
import feature.ukpt.client.ui.UkptState
import feature.ukpt.client.ui.UkptViewModel
import feature.ukpt.tests.ukptTestGraph
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.awaitMatching
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private fun UkptState.greetingTexts(): List<String>? = greetingSummary.getOrNull()?.greetings?.map { it.text }

private suspend fun <T> StateFlow<T>.awaitMatching(predicate: (T) -> Boolean): T =
    withTimeoutOrNull(10.seconds) { first(predicate) } ?: throw AssertionError("The sink did not match within 10 seconds. Last value: $value")

@SubcutaneousTest
class GreetingEventsTest {
    private val analytics = InMemoryAnalyticsSink<AnalyticsEvent>()
    private val metrics = InMemoryMetricsSink<Metric>()
    private val registrations = listOf(
        Trace.addSink(clientAnalyticsRules.sink(TraceSiteTable, analytics, CoroutineScope(Dispatchers.Unconfined))),
        Trace.addSink(clientMetricRules.sink(TraceSiteTable, metrics, CoroutineScope(Dispatchers.Unconfined))),
    )

    @AfterTest
    fun removeSinks() = registrations.forEach { it.close() }

    @Test
    fun `greeting records the click, then the sent greeting`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }
        screen.viewModel.onGreetClicked()
        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello") }

        analytics.events.awaitMatching { it.size == 2 }
        assertEquals(listOf(AnalyticsEvent.GreetClicked, AnalyticsEvent.GreetingSent), analytics.events.value)
    }

    @Test
    fun `the screen's load, the greeting and the summary's first value are timed`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }
        screen.viewModel.onGreetClicked()
        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello") }

        val timings = metrics.timings.awaitMatching { timings -> timings.any { it.metric is Metric.GreetDuration } }
        assertEquals(
            setOf(Metric.UkptScreenLoaded, Metric.GreetingSummaryLoad, Metric.GreetDuration(succeeded = true)),
            timings.map { it.metric }.toSet(),
        )
    }
}
