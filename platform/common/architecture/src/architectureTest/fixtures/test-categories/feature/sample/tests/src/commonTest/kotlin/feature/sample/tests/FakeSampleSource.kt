package feature.sample.tests

import feature.sample.client.domain.SampleSource
import platform.testing.subcutaneous.Recording

class FakeSampleSource : SampleSource {
    val requests = Recording<String>()

    override fun invoke(): String = "fake"
}
