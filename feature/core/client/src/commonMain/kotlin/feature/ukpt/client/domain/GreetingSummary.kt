package feature.ukpt.client.domain

import feature.ukpt.Greeting

data class GreetingSummary(
    val greetings: List<Greeting>,
) {
    val latest: Greeting? get() = greetings.lastOrNull()
}
