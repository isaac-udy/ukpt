package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FlowOfGreetingsImpl : FlowOfGreetings {
    override fun invoke(): Flow<Greeting> = flowOf(Greeting(text = "Hello"))
}
