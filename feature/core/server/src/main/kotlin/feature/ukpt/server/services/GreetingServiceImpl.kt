package feature.ukpt.server.services

import feature.ukpt.server.domain.FlowOfGreetingHistory
import feature.ukpt.server.domain.Greet
import feature.ukpt.server.domain.UpdateGreetingHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class GreetingServiceImpl(
    private val flowOfGreetingHistory: FlowOfGreetingHistory,
    private val greet: Greet,
    private val updateGreetingHistory: UpdateGreetingHistory,
) : GreetingService {

    override fun observeHistory(): Flow<GreetingService.ObserveHistory.Response> =
        flowOfGreetingHistory().map { GreetingService.ObserveHistory.Response(greetings = it) }

    override suspend fun greet(): GreetingService.Greet.Response =
        GreetingService.Greet.Response(greeting = greet.invoke())

    override suspend fun reset(): GreetingService.Reset.Response {
        updateGreetingHistory.reset()
        return GreetingService.Reset.Response
    }
}
