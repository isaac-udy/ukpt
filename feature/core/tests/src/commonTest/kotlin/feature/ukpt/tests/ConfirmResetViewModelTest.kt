package feature.ukpt.tests

import dev.enro.test.assertClosed
import dev.enro.test.assertCompleted
import feature.ukpt.client.ui.ConfirmResetDestination
import feature.ukpt.client.ui.ConfirmResetViewModel
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test

@SubcutaneousTest
class ConfirmResetViewModelTest {

    @Test
    fun `confirming completes the dialog`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<ConfirmResetViewModel, ConfirmResetDestination>(ConfirmResetDestination)

        screen.viewModel.onConfirm()

        screen.navigation.assertCompleted()
    }

    @Test
    fun `dismissing closes the dialog`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<ConfirmResetViewModel, ConfirmResetDestination>(ConfirmResetDestination)

        screen.viewModel.onDismiss()

        screen.navigation.assertClosed()
    }
}
