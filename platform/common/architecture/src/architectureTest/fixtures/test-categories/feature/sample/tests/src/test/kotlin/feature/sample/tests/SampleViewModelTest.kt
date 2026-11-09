package feature.sample.tests

import feature.sample.client.ui.SampleDestination
import feature.sample.client.ui.SampleViewModel
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.awaitMatching
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test

@SubcutaneousTest
class SampleViewModelTest {

    @Test
    fun refreshing() = subcutaneous(sampleTestGraph) {
        val screen = viewModel<SampleViewModel, SampleDestination>(SampleDestination)

        screen.viewModel.onRefresh()

        screen.viewModel.state.awaitMatching { it.label.isEmpty() }
    }
}
