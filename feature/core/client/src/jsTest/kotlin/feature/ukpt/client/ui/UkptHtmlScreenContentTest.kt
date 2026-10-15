package feature.ukpt.client.ui

import dev.isaacudy.udytils.state.AsyncState
import feature.ukpt.Greeting
import feature.ukpt.client.domain.GreetingSummary
import org.jetbrains.compose.web.testutils.ComposeWebExperimentalTestsApi
import org.jetbrains.compose.web.testutils.runTest
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ComposeWebExperimentalTestsApi::class)
class UkptHtmlScreenContentTest {
    private val populated = UkptState(
        greetingSummary = AsyncState.Success(
            GreetingSummary(greetings = listOf(Greeting(text = "Hi there"), Greeting(text = "Hello, ukpt!"))),
        ),
    )

    @Test
    fun successShowsTheLatestGreetingAndTheCount() = runTest {
        composition { UkptHtmlScreenContent(state = populated, onGreet = {}, onRetry = {}, onReset = {}) }

        assertEquals("Hello, ukpt!", root.querySelector("h1")?.textContent)
        assertEquals("2 greetings in history", root.querySelector("p")?.textContent)
    }

    @Test
    fun greetAndResetCallTheirCallbacks() = runTest {
        var greets = 0
        var resets = 0
        composition {
            UkptHtmlScreenContent(state = populated, onGreet = { greets++ }, onRetry = {}, onReset = { resets++ })
        }

        root.button("Greet").click()
        root.button("Reset").click()

        assertEquals(1, greets)
        assertEquals(1, resets)
    }

    @Test
    fun greetIsDisabledWhileAGreetIsInProgress() = runTest {
        composition {
            UkptHtmlScreenContent(
                state = populated.copy(greetAction = AsyncState.Loading()),
                onGreet = {},
                onRetry = {},
                onReset = {},
            )
        }

        assertTrue(root.button("Greeting…").disabled)
        assertFalse(root.button("Reset").disabled)
    }

    @Test
    fun errorOffersRetry() = runTest {
        var retries = 0
        composition {
            UkptHtmlScreenContent(
                state = UkptState(greetingSummary = AsyncState.Error(RuntimeException("Connection failed"))),
                onGreet = {},
                onRetry = { retries++ },
                onReset = {},
            )
        }

        assertEquals("Something went wrong", root.querySelector("h1")?.textContent)
        root.button("Retry").click()
        assertEquals(1, retries)
    }

    @Test
    fun confirmResetDialogCallsConfirmAndDismiss() = runTest {
        var confirms = 0
        var dismissals = 0
        composition { ConfirmResetDialogHtmlScreenContent(onConfirm = { confirms++ }, onDismiss = { dismissals++ }) }

        root.button("Cancel").click()
        root.button("Reset").click()

        assertEquals(1, confirms)
        assertEquals(1, dismissals)
    }

    private fun HTMLElement.button(label: String): HTMLButtonElement =
        querySelectorAll("button").asList()
            .filterIsInstance<HTMLButtonElement>()
            .single { it.textContent == label }
}
