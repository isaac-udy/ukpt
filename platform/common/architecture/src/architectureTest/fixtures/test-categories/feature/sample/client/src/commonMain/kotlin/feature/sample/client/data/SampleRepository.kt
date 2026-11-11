package feature.sample.client.data

import feature.sample.client.domain.SampleSource

internal class SampleRepository(private val source: SampleSource) {
    fun label(): String = source()
}
