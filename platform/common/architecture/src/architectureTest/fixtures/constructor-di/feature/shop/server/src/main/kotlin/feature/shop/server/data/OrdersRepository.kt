package feature.shop.server.data

import feature.shop.server.domain.GetOrders
import kotlin.time.Clock

internal class OrdersRepository(
    private val clock: Clock,
    private val config: OrdersConfig,
) {
    private val pageSize = 50

    val getOrders = GetOrders { emptyList() }
}
