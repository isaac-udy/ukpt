package feature.ukpt.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A worked-example use case implementing the `FlowOfGreetingHistory` domain interface from `:api`. */
internal class FlowOfGreetingHistoryImpl : FlowOfGreetingHistory {
    override fun invoke(): Flow<List<Greeting>> = flowOf(emptyList())
}
