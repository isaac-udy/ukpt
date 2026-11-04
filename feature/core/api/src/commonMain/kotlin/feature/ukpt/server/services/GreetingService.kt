package feature.ukpt.server.services

import dev.isaacudy.udytils.urpc.Urpc
import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Urpc("greeting")
interface GreetingService {
    fun observeHistory(): Flow<ObserveHistory.Response>
    suspend fun greet(): Greet.Response
    suspend fun reset(): Reset.Response

    object ObserveHistory {
        @Serializable data class Response(val greetings: List<Greeting>)
    }
    object Greet {
        @Serializable data class Response(val greeting: Greeting)
    }
    object Reset {
        @Serializable data object Response
    }
}
