package feature.sample.server.data

import feature.sample.server.domain.FlowOfSamples
import feature.sample.server.domain.SaveSample
import kotlinx.coroutines.flow.MutableStateFlow

internal class SampleRepository {
    private val samples = MutableStateFlow(emptyList<String>())

    val flowOfSamples = FlowOfSamples { samples }

    val saveSample = SaveSample { label -> samples.value += label }
}
