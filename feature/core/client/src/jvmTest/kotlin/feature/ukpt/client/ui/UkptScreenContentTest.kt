package feature.ukpt.client.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.isaacudy.udytils.state.AsyncState
import feature.ukpt.Greeting
import feature.ukpt.client.domain.GreetingSummary
import feature.ukpt.tests.onUkptScreen
import platform.design.UkptTheme
import platform.testing.ComponentTest
import platform.testing.robots.asUser
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@ComponentTest
class UkptScreenContentTest {

    @Test
    fun `a summary shows the latest greeting and the count`() = runComposeUiTest {
        setContent {
            UkptTheme {
                UkptScreenContent(
                    state = UkptState(greetingSummary = AsyncState.Success(GreetingSummary(listOf(Greeting("Hello"), Greeting("Hi there"))))),
                    onGreet = {},
                    onRetry = {},
                    onReset = {},
                )
            }
        }

        asUser {
            onUkptScreen {
                seeGreeting("Hi there")
                seeGreetingCount(2)
            }
        }
    }

    @Test
    fun `clicking Greet and Reset calls their callbacks`() = runComposeUiTest {
        val clicks = mutableListOf<String>()
        setContent {
            UkptTheme {
                UkptScreenContent(
                    state = UkptState(greetingSummary = AsyncState.Success(GreetingSummary(emptyList()))),
                    onGreet = { clicks += "greet" },
                    onRetry = {},
                    onReset = { clicks += "reset" },
                )
            }
        }

        asUser {
            onUkptScreen {
                seeNoGreetings()
                clickGreetButton()
                clickResetButton()
            }
        }
        assertEquals(listOf("greet", "reset"), clicks)
    }
}
