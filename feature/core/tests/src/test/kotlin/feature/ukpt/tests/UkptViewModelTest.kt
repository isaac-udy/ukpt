package feature.ukpt.tests

import dev.enro.test.assertOpened
import dev.enro.test.sendClosedForTest
import dev.enro.test.sendCompletedForTest
import dev.isaacudy.udytils.state.getOrNull
import feature.ukpt.client.ui.ConfirmResetDestination
import feature.ukpt.client.ui.UkptDestination
import feature.ukpt.client.ui.UkptState
import feature.ukpt.client.ui.UkptViewModel
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.awaitMatching
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test
import kotlin.test.assertEquals

@SubcutaneousTest
class UkptViewModelTest {

    @Test
    fun `an empty database loads an empty history`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)

        val state = screen.viewModel.state.awaitMatching { it.greetingTexts() != null }

        assertEquals(emptyList(), state.greetingTexts())
    }

    @Test
    fun `greeting twice says Hello then Hello again`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }

        screen.viewModel.onGreetClicked()
        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello") }
        screen.viewModel.onGreetClicked()

        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello", "Hello again") }
    }

    @Test
    fun `a greeting added while the screen is open appears`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }

        given { greetingHistory("Kia ora") }

        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Kia ora") }
    }

    @Test
    fun `confirming the reset dialog clears the history`() = subcutaneous(ukptTestGraph) {
        given { greetingHistory("Hello", "Hello again") }
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts()?.size == 2 }

        screen.viewModel.onResetRequested()
        screen.navigation.assertOpened<ConfirmResetDestination>().sendCompletedForTest()

        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }
    }

    @Test
    fun `closing the reset dialog keeps the history`() = subcutaneous(ukptTestGraph) {
        given { greetingHistory("Hello") }
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello") }

        screen.viewModel.onResetRequested()
        screen.navigation.assertOpened<ConfirmResetDestination>().sendClosedForTest()
        screen.viewModel.onGreetClicked()

        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello", "Hello again") }
    }

    @Test
    fun `retrying reloads the history`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }
        given { greetingHistory("Hello") }

        screen.viewModel.onRetryClicked()

        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello") }
    }
}

private fun UkptState.greetingTexts(): List<String>? = greetingSummary.getOrNull()?.greetings?.map { it.text }
