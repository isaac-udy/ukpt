package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.state.getOrNull
import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.Trace
import feature.ukpt.client.ui.UkptDestination
import feature.ukpt.client.ui.UkptState
import feature.ukpt.client.ui.UkptViewModel
import feature.ukpt.tests.ukptTestGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.awaitMatching
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private fun UkptState.greetingTexts(): List<String>? = greetingSummary.getOrNull()?.greetings?.map { it.text }

@SubcutaneousTest
class GreetingTraceTest {
    private val sink = InMemorySpanSink()
    private val registration = Trace.addSink(sink)

    @AfterTest
    fun removeSink() = registration.close()

    private fun span(name: String): Span? = sink.spans.lastOrNull { it.site.name == name }

    @Test
    fun `the server's greet span continues the client's trace through traceparent`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }
        screen.viewModel.onGreetClicked()
        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello") }
        withTimeoutOrNull(10.seconds) { while (span(CLIENT_GREET) == null || span(SERVER_GREET) == null) delay(10) }

        val client = span(CLIENT_GREET)!!
        val server = span(SERVER_GREET)!!
        assertEquals(client.traceId, server.traceId)
        assertTrue(server.parent!!.isRemote, "the server span's parent arrived in the call's metadata")
        assertEquals(client.spanId, server.parentSpanId)
    }

    private companion object {
        const val CLIENT_GREET = "feature.ukpt.client.data.GreetingRepository.greet"
        const val SERVER_GREET = "feature.ukpt.server.services.GreetingServiceImpl.greet"
    }
}
