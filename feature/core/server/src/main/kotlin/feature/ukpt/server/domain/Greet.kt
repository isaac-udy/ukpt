package feature.ukpt.server.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.first

fun interface Greet {
    suspend operator fun invoke(): Greeting
}

internal class GreetImpl(
    private val flowOfGreetingHistory: FlowOfGreetingHistory,
    private val updateGreetingHistory: UpdateGreetingHistory,
) : Greet {
    override suspend fun invoke(): Greeting {
        val history = flowOfGreetingHistory().first()
        val greeting = Greeting(text = if (history.isEmpty()) "Hello" else "Hello again")
        updateGreetingHistory.add(greeting.text)
        return greeting
    }
}
