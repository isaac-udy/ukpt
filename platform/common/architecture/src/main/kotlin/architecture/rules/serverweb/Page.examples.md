A Page renders its View State through the layout and composes Components. The list connects to
its event stream through a sink element that swaps nothing itself:

```kotlin
internal fun HTML.ordersPage(state: OrdersPageState) {
    ukptLayout(LayoutState(title = "Orders", scripts = listOf(OrdersPaths.QUANTITY_SCRIPT))) {
        h1 { +"Orders" }
        orderForm(state.form, state.errors)
        orderList(state.orders)
    }
}

internal fun FlowContent.orderList(orders: List<Order>) {
    ul("list") {
        elementId = OrdersIds.orders
        orders.forEach { orderItem(it) }
    }
    div {
        sse {
            connect(OrdersPaths.ORDER_EVENTS)
            swap(OrdersIds.ORDERS_EVENT)
        }
        hx { swap(SwapStyle.None) }
    }
}

internal fun UL.orderItem(order: Order) {
    li {
        elementId = OrdersIds.order(order.id)
        +order.summary
    }
}
```
