package feature.sample.tests

import feature.sample.server.domain.FlowOfSamples
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeFlowOfSamples : FlowOfSamples {
    override fun invoke(): Flow<List<String>> = flowOf(listOf("fake"))
}
