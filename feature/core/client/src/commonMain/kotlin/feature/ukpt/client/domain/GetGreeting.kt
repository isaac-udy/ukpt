package feature.ukpt.client.domain

fun interface GetGreeting {
    suspend operator fun invoke(): String
}
