package feature.ukpt.tests

import feature.ukpt.Greeting
import feature.ukpt.server.services.GreetingService
import kotlinx.coroutines.flow.first
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test
import kotlin.test.assertEquals

@SubcutaneousTest
class GreetingServiceTest {

    @Test
    fun `greet answers Hello on an empty history, then Hello again`() = subcutaneous(ukptTestGraph) {
        val greetingService = service<GreetingService>()

        assertEquals(Greeting(text = "Hello"), greetingService.greet().greeting)
        assertEquals(Greeting(text = "Hello again"), greetingService.greet().greeting)
    }

    @Test
    fun `reset empties the history`() = subcutaneous(ukptTestGraph) {
        given { greetingHistory("Hello", "Hello again") }
        val greetingService = service<GreetingService>()

        greetingService.reset()

        assertEquals(emptyList(), greetingService.observeHistory().first().greetings)
    }

    @Test
    fun `observing the history returns the seeded greetings in order`() = subcutaneous(ukptTestGraph) {
        val seeded = given { greetingHistory("Kia ora", "Hello") }

        val history = service<GreetingService>().observeHistory().first().greetings

        assertEquals(seeded, history)
    }
}
