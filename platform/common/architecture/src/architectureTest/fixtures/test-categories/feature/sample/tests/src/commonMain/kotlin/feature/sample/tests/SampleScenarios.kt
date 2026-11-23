package feature.sample.tests

import feature.sample.client.domain.SampleSource
import platform.testing.subcutaneous.Scenario

suspend fun Scenario.sampleLabel(): String = client<SampleSource>()()
