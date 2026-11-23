package feature.sample.tests

import feature.sample.client.domain.SampleSource

class FakeSampleSource : SampleSource {
    override fun invoke(): String = "fake"
}
