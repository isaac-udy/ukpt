package feature.shop.server.data

import kotlin.time.Clock

internal class InvoicesClient(
    private val clock: Clock,
) {
    private val config = OrdersConfig(region = "eu")
}
