package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FlowOfGreetingHistoryImpl : FlowOfGreetingHistory {
    override fun invoke(): Flow<List<Greeting>> = flowOf(emptyList())
}
