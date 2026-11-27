package com.isaacudy.ukpt.telemetry

import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.SpanKind
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.selecting
import dev.isaacudy.udytils.state.getOrNull
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
import kotlin.time.Duration.Companion.seconds

private fun UkptState.greetingTexts(): List<String>? = greetingSummary.getOrNull()?.greetings?.map { it.text }

@SubcutaneousTest
class ConstructSelectionTest {
    private val sink = InMemorySpanSink(TraceSiteTable.selecting(Constructs.ClientData.Repository))
    private val registration = Trace.addSink(sink)

    @AfterTest
    fun removeSink() = registration.close()

    @Test
    fun `a repository rule selects only the repository's sites`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }
        screen.viewModel.onGreetClicked()
        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello") }
        withTimeoutOrNull(10.seconds) { while (sink.spans.none { it.site.name.endsWith(".greet") }) delay(10) }

        assertEquals(
            setOf(
                "feature.ukpt.client.data.GreetingRepository.<init>" to SpanKind.Construction,
                "feature.ukpt.client.data.GreetingRepository.flowOfGreetingSummary" to SpanKind.FirstValue,
                "feature.ukpt.client.data.GreetingRepository.greet" to SpanKind.Call,
            ),
            sink.spans.map { it.site.name to it.kind }.toSet(),
        )
    }
}
