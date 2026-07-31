package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow

/**
 * A worked-example `FlowOf…` domain interface returning `Flow<List<Greeting>>` — a stream of a
 * collection of domain objects. Implemented by `FlowOfGreetingHistoryImpl` in `:client`.
 */
fun interface FlowOfGreetingHistory {
    operator fun invoke(): Flow<List<Greeting>>
}
