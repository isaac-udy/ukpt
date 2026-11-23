package feature.sample.tests

import feature.sample.client.data.SampleRepository
import feature.sample.client.ui.SampleViewModel
import org.koin.core.Koin
import platform.testing.SubcutaneousTest
import kotlin.test.Test
import kotlin.test.assertTrue

@SubcutaneousTest
class ShortcutSubcutaneousTest {

    @Test
    fun constructsItsOwnViewModel() {
        // Names subcutaneous(sampleTestGraph) and viewModel.onDelete() only in this comment.
        val viewModel = SampleViewModel()
        SampleRepository(source = { "sample" })
        assertTrue(viewModel.state.value.label.isEmpty())
    }
}
