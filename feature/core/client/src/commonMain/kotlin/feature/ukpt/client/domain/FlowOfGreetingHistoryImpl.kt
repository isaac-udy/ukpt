package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A worked-example use case implementing the `FlowOfGreetingHistory` domain interface declared beside it in `:client`. */
internal class FlowOfGreetingHistoryImpl : FlowOfGreetingHistory {
    override fun invoke(): Flow<List<Greeting>> = flowOf(emptyList())
}
