package feature.sample.tests

import feature.sample.server.domain.AddSample
import feature.sample.server.domain.FlowOfSamples
import kotlinx.coroutines.flow.first
import platform.testing.subcutaneous.Scenario

suspend fun Scenario.samples(vararg labels: String): List<String> {
    val addSample = server<AddSample>()
    labels.forEach { addSample(it) }
    return server<FlowOfSamples>()().first()
}
