package feature.ukpt.client.domain

import feature.ukpt.Greeting
/**
 * A worked-example plain domain interface: a `suspend`, value-returning `operator fun invoke`.
 * The contract lives in `:client` beside its use-case implementation (`GetGreetingImpl`) — a
 * domain interface moves to `:api` (same package, a file move) only when a second feature
 * consumes it.
 */
fun interface GetGreeting {
    suspend operator fun invoke(): String
}
