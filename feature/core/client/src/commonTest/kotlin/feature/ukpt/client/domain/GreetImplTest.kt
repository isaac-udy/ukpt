package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GreetImplTest {

    private val updates = mutableListOf<UpdateGreetings.Update>()
    private val updateGreetings = UpdateGreetings { updates += it }

    @Test
    fun firstGreetingSaysHello() = runTest {
        val greet = GreetImpl(
            flowOfGreetingSummary = FlowOfGreetingSummary { flowOf(GreetingSummary(greetings = emptyList())) },
            updateGreetings = updateGreetings,
        )

        greet()

        assertEquals(listOf<UpdateGreetings.Update>(UpdateGreetings.Update.Add(text = "Hello")), updates)
    }

    @Test
    fun laterGreetingsSayHelloAgain() = runTest {
        val greet = GreetImpl(
            flowOfGreetingSummary = FlowOfGreetingSummary {
                flowOf(GreetingSummary(greetings = listOf(Greeting(text = "Hello"))))
            },
            updateGreetings = updateGreetings,
        )

        greet()

        assertEquals(listOf<UpdateGreetings.Update>(UpdateGreetings.Update.Add(text = "Hello again")), updates)
    }
}
