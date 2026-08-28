package feature.ukpt.client.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class FlowOfGreetingSummaryImpl(
    private val flowOfLatestGreeting: FlowOfLatestGreeting,
    private val flowOfGreetingHistory: FlowOfGreetingHistory,
) : FlowOfGreetingSummary {

    override fun invoke(): Flow<GreetingSummary> = combine(
        flowOfLatestGreeting(),
        flowOfGreetingHistory(),
    ) { latest, history ->
        GreetingSummary(
            latestGreeting = latest,
            greetingHistory = history,
        )
    }
}
