A domain model that holds domain interfaces — a violation:

```kotlin
// feature/shop/client/domain/CheckoutInputs.kt
package feature.shop.client.domain

data class CheckoutInputs(
    val getShippingOptions: GetShippingOptions,
    val calculateTotal: Lazy<CalculateTotal>,
)
```

The corrected form: the consumer injects each interface directly.

```kotlin
// feature/shop/client/ui/CheckoutViewModel.kt
package feature.shop.client.ui

class CheckoutViewModel(
    private val getShippingOptions: GetShippingOptions,
    private val calculateTotal: CalculateTotal,
) : ViewModel() { ... }
```
