A UseCase exists for a decision over capabilities that exist independently of it; the Repository stores what it is told.

```kotlin
// feature/shop/client/domain/ReorderImpl.kt
package feature.shop.client.domain

internal class ReorderImpl(
    private val getOrder: GetOrder,
    private val updateCart: UpdateCart,
) : Reorder {
    override suspend fun invoke(id: OrderId) {
        val order = getOrder(id) ?: throw OrderNotFoundException()
        val available = order.lines.filter { it.stillAvailable }
        if (available.isEmpty()) throw NothingToReorderException()
        updateCart.addLines(available)
    }
}
```

An implementation step with one caller is a private function of that caller, not a further domain interface.

```kotlin
// feature/shop/client/domain/RefreshCartPricesImpl.kt
package feature.shop.client.domain

internal class RefreshCartPricesImpl(
    private val getCart: GetCart,
    private val getPrices: GetPrices,
    private val updateCart: UpdateCart,
) : RefreshCartPrices {
    override suspend fun invoke(cartId: CartId) {
        val cart = getCart(cartId) ?: return
        val prices = getPrices(cart.lines.map { it.productId })
        updateCart.prices(cartId, reprice(cart, prices))
    }

    private fun reprice(cart: Cart, prices: Map<ProductId, Money>): List<CartLine> { ... }
}
```
