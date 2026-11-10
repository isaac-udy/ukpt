package feature.ukpt.server.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import platform.testing.UnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

@UnitTest
class GreetImplTest {

    private val updates = mutableListOf<UpdateGreetingHistory.Update>()
    private val updateGreetingHistory = UpdateGreetingHistory { updates += it }

    @Test
    fun `a first greeting says hello`() = runTest {
        val greet = GreetImpl(
            flowOfGreetingHistory = { flowOf(emptyList()) },
            updateGreetingHistory = updateGreetingHistory,
        )

        greet("Ada")

        assertEquals(listOf<UpdateGreetingHistory.Update>(UpdateGreetingHistory.Update.Add(text = "Hello, Ada")), updates)
    }

    @Test
    fun `a name greeted before is greeted again`() = runTest {
        val greet = GreetImpl(
            flowOfGreetingHistory = { flowOf(listOf(Greeting(id = "1", text = "Hello, Ada"))) },
            updateGreetingHistory = updateGreetingHistory,
        )

        greet("Ada")

        assertEquals(listOf<UpdateGreetingHistory.Update>(UpdateGreetingHistory.Update.Add(text = "Hello again, Ada")), updates)
    }
}
