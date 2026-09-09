package feature.ukpt.client.domain

import kotlinx.coroutines.flow.first

internal class GreetImpl(
    private val flowOfGreetingSummary: FlowOfGreetingSummary,
    private val updateGreetings: UpdateGreetings,
) : Greet {
    override suspend fun invoke() {
        val summary = flowOfGreetingSummary().first()
        val text = if (summary.greetings.isEmpty()) "Hello" else "Hello again"
        updateGreetings.add(text)
    }
}
