package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow

fun interface FlowOfLatestGreeting {
    operator fun invoke(): Flow<Greeting?>
}
