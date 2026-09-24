package feature.ukpt.server.domain

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

        greet("Ada")

        assertEquals(listOf<UpdateGreetings.Update>(UpdateGreetings.Update.Add(text = "Hello, Ada")), updates)
    }

    @Test
    fun laterGreetingsSayHelloAgain() = runTest {
        val greet = GreetImpl(
            flowOfGreetingSummary = FlowOfGreetingSummary {
                flowOf(GreetingSummary(greetings = listOf(Greeting(id = 1, text = "Hello, Ada"))))
            },
            updateGreetings = updateGreetings,
        )

        greet("Ada")

        assertEquals(listOf<UpdateGreetings.Update>(UpdateGreetings.Update.Add(text = "Hello again, Ada")), updates)
    }
}
