package feature.shop.client.domain

fun interface Reorder {
    suspend operator fun invoke()
}
