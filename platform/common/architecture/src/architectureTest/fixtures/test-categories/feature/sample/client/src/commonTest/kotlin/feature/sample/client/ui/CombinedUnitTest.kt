package feature.sample.client.ui

import feature.sample.client.data.SampleRepository
import org.koin.core.Koin
import platform.testing.UnitTest
import kotlin.test.Test

@UnitTest
class CombinedUnitTest {

    @Test
    fun combinesTwoClasses() {
        val repository = SampleRepository()
        val viewModel = SampleViewModel()
    }
}
