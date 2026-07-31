package feature.ukpt.client.domain

import feature.ukpt.Greeting
/**
 * A worked-example use case: implements the `GetGreeting` domain interface declared in the sibling
 * `:api` module. This is the `[DomainInterface]Impl` shape the architecture rules match.
 */
internal class GetGreetingImpl : GetGreeting {
    override suspend fun invoke(): String = "Hello"
}
