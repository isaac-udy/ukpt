package feature.shop.server.domain

fun interface GetOrders {
    suspend operator fun invoke(): List<String>
}
