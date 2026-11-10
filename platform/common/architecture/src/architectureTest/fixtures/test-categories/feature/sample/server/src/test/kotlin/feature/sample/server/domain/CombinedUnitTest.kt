package feature.sample.server.domain

import feature.sample.server.data.SampleRepository
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import platform.testing.UnitTest
import kotlin.test.Test

@UnitTest
class CombinedUnitTest {

    @Test
    fun `adding a sample saves it`() = runTest {
        val repository = SampleRepository()
        AddSampleImpl(repository.flowOfSamples, repository.saveSample)("a")
    }
}
