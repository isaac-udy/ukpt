package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow

fun interface FlowOfGreetingHistory {
    operator fun invoke(): Flow<List<Greeting>>
}
