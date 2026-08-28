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
    fun getGreetingAppendsToHistory() = runTest {
        val repo = GreetingRepository()
        repo.getGreeting()
        val history = repo.flowOfGreetingHistory().first()
        assertEquals(1, history.size)
        assertEquals("Hello", history.first().text)
    }

    @Test
    fun getGreetingUpdatesLatest() = runTest {
        val repo = GreetingRepository()
        assertNull(repo.flowOfLatestGreeting().first())

        repo.getGreeting()
        assertEquals(Greeting(text = "Hello"), repo.flowOfLatestGreeting().first())
    }

    @Test
    fun resetGreetingsClearsList() = runTest {
        val repo = GreetingRepository()
        repo.getGreeting()
        repo.getGreeting()
        assertEquals(2, repo.flowOfGreetingHistory().first().size)

        repo.resetGreetings()
        assertTrue(repo.flowOfGreetingHistory().first().isEmpty())
        assertNull(repo.flowOfLatestGreeting().first())
    }

    @Test
    fun multipleGreetsAccumulate() = runTest {
        val repo = GreetingRepository()
        repo.getGreeting()
        repo.getGreeting()
        repo.getGreeting()

        val history = repo.flowOfGreetingHistory().first()
        assertEquals(3, history.size)
        assertEquals(Greeting(text = "Hello"), repo.flowOfLatestGreeting().first())
    }
}
