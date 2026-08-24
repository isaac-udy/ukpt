A domain model that holds domain interfaces — a violation:

```kotlin
// feature/shop/server/domain/CheckoutInputs.kt
package feature.shop.server.domain

data class CheckoutInputs(
    val getShippingOptions: GetShippingOptions,
    val calculateTotal: Lazy<CalculateTotal>,
)
```

The corrected form: the consumer injects each interface directly.

```kotlin
// feature/shop/server/domain/CheckoutUseCase.kt
package feature.shop.server.domain

class CheckoutUseCase(
    private val getShippingOptions: GetShippingOptions,
    private val calculateTotal: CalculateTotal,
) { ... }
```
