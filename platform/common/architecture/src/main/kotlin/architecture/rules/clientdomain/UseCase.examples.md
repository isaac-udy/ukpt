A UseCase exists for a decision over capabilities that exist independently of it; the Repository stores what it is told. It shares its interface's file when both are in the same module and package; the implementation of an interface published to `:api` has its own file in the client module.

```kotlin
// feature/shop/client/domain/Reorder.kt
package feature.shop.client.domain

fun interface Reorder {
    suspend operator fun invoke(id: OrderId)
}

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
// feature/shop/client/domain/RefreshCartPrices.kt
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
