A Repository providing domain interfaces over the tables its StorageClasses own. A domain model that spans three tables is assembled behind one property, inside one transaction:

```kotlin
internal class OrdersRepository(
    private val orderStorage: OrderStorage,
    private val orderLineStorage: OrderLineStorage,
    private val paymentStorage: PaymentStorage,
    private val transactionRunner: TransactionRunner,
) {
    val getOrder = GetOrder { id ->
        transactionRunner.inTransaction {
            val order = orderStorage.getById(id) ?: return@inTransaction null
            val lines = orderLineStorage.listForOrder(id)
            val payment = paymentStorage.getForOrder(id)
            order.toDomain(lines = lines.map { it.toDomain() }, payment = payment?.toDomain())
        }
    }

    val flowOfOrdersForCustomer = FlowOfOrdersForCustomer { customerId ->
        orderStorage.observeForCustomer(customerId).map { rows -> rows.map { it.toDomain() } }
    }
}
```

The consumer before the Repository assembled the model: one interface per fact, joined at every call site.

```kotlin
internal class SubmitOrderImpl(
    private val getOrderHeader: GetOrderHeader,
    private val getOrderLines: GetOrderLines,
    private val getOrderPayment: GetOrderPayment,
    private val markOrderSubmitted: MarkOrderSubmitted,
) : SubmitOrder {
    override suspend fun invoke(id: OrderId) {
        val header = getOrderHeader(id) ?: throw OrderNotFoundException()
        val lines = getOrderLines(id)
        val payment = getOrderPayment(id)
        if (lines.isEmpty() || payment == null) throw OrderIncompleteException()
        markOrderSubmitted(id)
    }
}
```

The consumer after: `GetOrder` returns the `Order`, and three interfaces, three Repository properties, and three Koin bindings are gone.

```kotlin
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

A read that stays separate: shipment tracking comes from a carrier IntegrationClient, its failure must not fail an order read, and the order screen renders it as an optional resource.

```kotlin
fun interface FlowOfOrderShipmentTracking {
    operator fun invoke(id: OrderId): Flow<ShipmentTracking?>
}
```
