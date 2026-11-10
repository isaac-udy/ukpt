package feature.sample.server.domain

import kotlinx.coroutines.flow.Flow

fun interface FlowOfSamples {
    operator fun invoke(): Flow<List<String>>
}
