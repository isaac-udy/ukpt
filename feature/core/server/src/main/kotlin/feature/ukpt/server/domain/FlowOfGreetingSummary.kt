package feature.ukpt.server.domain

import kotlinx.coroutines.flow.Flow

fun interface FlowOfGreetingSummary {
    operator fun invoke(): Flow<GreetingSummary>
}
