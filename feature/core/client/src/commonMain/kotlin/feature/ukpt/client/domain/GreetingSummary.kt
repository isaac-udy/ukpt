package feature.ukpt.client.domain

import feature.ukpt.Greeting

data class GreetingSummary(
    val latestGreeting: Greeting?,
    val greetingHistory: List<Greeting>,
)
