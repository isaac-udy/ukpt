package feature.ukpt.client.domain

import feature.ukpt.Greeting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A worked-example use case implementing the `FlowOfLatestGreeting` domain interface from `:api`. */
internal class FlowOfLatestGreetingImpl : FlowOfLatestGreeting {
    override fun invoke(): Flow<Greeting?> = flowOf(null)
}
