package feature.shop.server.domain

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun interface ReconcileOrders {
    suspend operator fun invoke()
}

internal class ReconcileOrdersImpl(
    private val getOrders: GetOrders,
    private val clock: Clock = Clock.System,
    private val cleanupTimeout: Duration = 15.seconds,
) : ReconcileOrders {
    override suspend fun invoke() = Unit
}
