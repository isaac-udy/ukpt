package feature.ukpt.client.domain

internal class GetGreetingImpl : GetGreeting {
    override suspend fun invoke(): String = "Hello"
}
