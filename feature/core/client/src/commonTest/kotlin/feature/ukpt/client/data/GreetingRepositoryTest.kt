package feature.ukpt.client.data

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GreetingRepositoryTest {

    @Test
    fun addAppendsToGreetings() = runTest {
        val repo = GreetingRepository()
        repo.updateGreetings.add("Hello")

        val summary = repo.flowOfGreetingSummary().first()
        assertEquals(listOf(Greeting(text = "Hello")), summary.greetings)
    }

    @Test
    fun latestIsTheMostRecentGreeting() = runTest {
        val repo = GreetingRepository()
        assertNull(repo.flowOfGreetingSummary().first().latest)

        repo.updateGreetings.add("Hello")
        repo.updateGreetings.add("Hello again")
        assertEquals(Greeting(text = "Hello again"), repo.flowOfGreetingSummary().first().latest)
    }

    @Test
    fun resetClearsGreetings() = runTest {
        val repo = GreetingRepository()
        repo.updateGreetings.add("Hello")
        repo.updateGreetings.add("Hello again")
        assertEquals(2, repo.flowOfGreetingSummary().first().greetings.size)

        repo.updateGreetings.reset()
        val summary = repo.flowOfGreetingSummary().first()
        assertTrue(summary.greetings.isEmpty())
        assertNull(summary.latest)
    }
}
