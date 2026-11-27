package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.trace.InMemorySpanSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.enro.TraceNavigationPlugin
import dev.isaacudy.udytils.trace.rules.InMemoryAnalyticsSink
import feature.ukpt.client.ui.UkptDestination
import feature.ukpt.client.ui.UkptViewModel
import feature.ukpt.tests.ukptTestGraph
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.awaitAsserted
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ON_GREET_CLICKED = "feature.ukpt.client.ui.UkptViewModel.onGreetClicked"

@SubcutaneousTest
class ScreenVisitTest {
    private val analytics = InMemoryAnalyticsSink<AnalyticsEvent>()
    private val spans = InMemorySpanSink()
    private val plugin = TraceNavigationPlugin()
    private val registrations = listOf(
        Trace.addSink(clientAnalyticsRules.sink(TraceSiteTable, analytics, CoroutineScope(Dispatchers.Unconfined))),
        Trace.addSink(spans),
        Trace.linkRoots(plugin::visitOf),
    )

    @AfterTest
    fun removeSinks() = registrations.forEach { it.close() }

    @Test
    fun `opening the screen records the screen view, and the screen's intents link to the visit`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        plugin.onOpened(screen.navigation)
        assertEquals(listOf(AnalyticsEvent.UkptScreenViewed), analytics.events.value)

        screen.viewModel.onGreetClicked()
        plugin.onClosed(screen.navigation)

        awaitAsserted {
            val visit = spans.spans.single { it.site.isVisit }
            assertEquals(listOf(visit), spans.spans.single { it.site.name == ON_GREET_CLICKED }.links)
        }
    }
}
