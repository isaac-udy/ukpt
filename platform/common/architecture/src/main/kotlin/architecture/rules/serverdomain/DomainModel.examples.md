A domain model that holds domain interfaces — a violation:

```kotlin
// feature/shop/server/domain/CheckoutInputs.kt
package feature.shop.server.domain

data class CheckoutInputs(
    val getShippingOptions: GetShippingOptions,
    val calculateTotal: Lazy<CalculateTotal>,
)
```

The corrected form when the consumer needs the capabilities: it injects each interface directly.

```kotlin
// feature/shop/server/domain/CheckoutUseCase.kt
package feature.shop.server.domain

class CheckoutUseCase(
    private val getShippingOptions: GetShippingOptions,
    private val calculateTotal: CalculateTotal,
) { ... }
```

The corrected form when the consumer needs the values: one domain interface returns a model carrying them, and the consumer injects that one interface.

```kotlin
// feature/shop/server/domain/CheckoutInputs.kt
package feature.shop.server.domain

data class CheckoutInputs(
    val shippingOptions: List<ShippingOption>,
    val total: Money,
)

fun interface GetCheckoutInputs {
    suspend operator fun invoke(cartId: CartId): CheckoutInputs
}
```
