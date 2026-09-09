package feature.shop.server.data

internal data class OrdersConfig(
    val region: String,
    val retries: Int = 3,
)
