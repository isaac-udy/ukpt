package feature.ukpt.server.domain

import kotlinx.coroutines.flow.first

fun interface Greet {
    suspend operator fun invoke(name: String)
}

internal class GreetImpl(
    private val flowOfGreetingHistory: FlowOfGreetingHistory,
    private val updateGreetingHistory: UpdateGreetingHistory,
) : Greet {
    override suspend fun invoke(name: String) {
        val greeted = flowOfGreetingHistory().first().any { it.text.endsWith(", $name") }
        updateGreetingHistory.add(if (greeted) "Hello again, $name" else "Hello, $name")
    }
}
