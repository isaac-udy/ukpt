A Routes class answering a page load, a form post from htmx or a plain form, and an event stream:

```kotlin
internal class OrdersRoutes(
    private val flowOfOrders: FlowOfOrders,
    private val placeOrder: PlaceOrder,
) : WebRoutes {

    override fun Route.install() {
        get(OrdersPaths.ORDERS) {
            val orders = flowOfOrders().first()
            call.respondHtml { ordersPage(OrdersPageState(orders, OrderForm.Empty, errors = null)) }
        }

        post(OrdersPaths.ORDERS) {
            val form = OrderForm.from(call.receiveParameters())
            call.varyOnHtmx()
            when (val result = form.validate()) {
                is FormResult.Invalid -> if (call.isHtmx) {
                    call.respondHtmlFragment(HttpStatusCode.UnprocessableEntity) { orderForm(form, result.errors) }
                } else {
                    val orders = flowOfOrders().first()
                    call.respondHtml(HttpStatusCode.UnprocessableEntity) { ordersPage(OrdersPageState(orders, form, result.errors)) }
                }
                is FormResult.Valid -> {
                    placeOrder(result.value)
                    if (call.isHtmx) call.respondHtmlFragment { orderForm(OrderForm.Empty, errors = null) } else call.respondSeeOther(OrdersPaths.ORDERS)
                }
            }
        }

        sse(OrdersPaths.ORDER_EVENTS) {
            orderEvents(flowOfOrders).collect { send(it) }
        }
    }
}
```

The dependency module binds it:

```kotlin
singleOf(::OrdersRoutes) bind WebRoutes::class
```
