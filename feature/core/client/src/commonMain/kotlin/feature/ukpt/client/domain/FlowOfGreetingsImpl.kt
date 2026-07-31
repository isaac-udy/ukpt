package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A worked-example use case implementing the `FlowOfGreetings` domain interface from `:api`. */
internal class FlowOfGreetingsImpl : FlowOfGreetings {
    override fun invoke(): Flow<Greeting> = flowOf(Greeting(text = "Hello"))
}
