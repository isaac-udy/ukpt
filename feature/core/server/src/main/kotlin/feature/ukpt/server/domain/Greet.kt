package feature.ukpt.server.domain

import kotlinx.coroutines.flow.first

fun interface Greet {
    suspend operator fun invoke(name: String)
}

internal class GreetImpl(
    private val flowOfGreetingSummary: FlowOfGreetingSummary,
    private val updateGreetings: UpdateGreetings,
) : Greet {
    override suspend fun invoke(name: String) {
        val summary = flowOfGreetingSummary().first()
        val greeted = summary.greetings.any { it.text.endsWith(", $name") }
        updateGreetings.add(if (greeted) "Hello again, $name" else "Hello, $name")
    }
}
