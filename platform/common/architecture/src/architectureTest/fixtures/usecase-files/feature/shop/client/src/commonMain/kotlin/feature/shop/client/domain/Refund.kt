package feature.shop.client.domain

fun interface Refund {
    suspend operator fun invoke()
}

internal class RefundImpl : Refund {
    override suspend fun invoke() = Unit
}
