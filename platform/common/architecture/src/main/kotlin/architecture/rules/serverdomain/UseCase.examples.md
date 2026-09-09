A UseCase exists for a decision over capabilities that exist independently of it; the Repository stores what it is told. It shares its interface's file when both are in the same module and package; the implementation of an interface published to `:api` has its own file in the server module.

```kotlin
// feature/shop/server/domain/SubmitOrder.kt
package feature.shop.server.domain

fun interface SubmitOrder {
    suspend operator fun invoke(id: OrderId)
}

internal class SubmitOrderImpl(
    private val getOrder: GetOrder,
    private val updateOrder: UpdateOrder,
) : SubmitOrder {
    override suspend fun invoke(id: OrderId) {
        val order = getOrder(id) ?: throw OrderNotFoundException()
        if (order.lines.isEmpty() || order.payment == null) throw OrderIncompleteException()
        updateOrder.submitted(id)
    }
}
```

Phases of an orchestration with one caller are private functions of that caller, not further domain interfaces; phase order, failure isolation, and cancellation stay visible in one place.

```kotlin
// feature/shop/server/domain/ReconcileOrders.kt
package feature.shop.server.domain

internal class ReconcileOrdersImpl(
    private val getOrdersAwaitingReconciliation: GetOrdersAwaitingReconciliation,
    private val getPaymentRecord: GetPaymentRecord,
    private val updateOrder: UpdateOrder,
) : ReconcileOrders {
    override suspend fun invoke() {
        for (order in getOrdersAwaitingReconciliation()) {
            reconcilePayment(order)
            releaseExpiredHold(order)
        }
    }

    private suspend fun reconcilePayment(order: Order) { ... }

    private suspend fun releaseExpiredHold(order: Order) { ... }
}
```
