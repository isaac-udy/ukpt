package feature.shop.client.domain

fun interface Checkout {
    suspend operator fun invoke()
}
