package feature.ukpt.client.domain

import feature.ukpt.Greeting
/**
 * A worked-example plain domain interface: a `suspend`, value-returning `operator fun invoke`.
 * The contract lives in `:api`; its use-case implementation (`GetGreetingImpl`) lives in `:client`.
 */
fun interface GetGreeting {
    suspend operator fun invoke(): String
}
