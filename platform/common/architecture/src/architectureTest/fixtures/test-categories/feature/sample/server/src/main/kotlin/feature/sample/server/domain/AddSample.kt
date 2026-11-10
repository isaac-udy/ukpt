package feature.sample.server.domain

import kotlinx.coroutines.flow.first

fun interface AddSample {
    suspend operator fun invoke(label: String)
}

internal class AddSampleImpl(
    private val flowOfSamples: FlowOfSamples,
    private val saveSample: SaveSample,
) : AddSample {
    override suspend fun invoke(label: String) {
        val trimmed = label.trim()
        if (trimmed !in flowOfSamples().first()) saveSample(trimmed)
    }
}
