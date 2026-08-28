A computed read projection that groups related domain objects into a single consistent snapshot. The projection preserves domain objects rather than flattening to display strings; the domain interface that produces it groups by consistency and failure boundary, not by screen.

```kotlin
// feature/shop/client/domain/UserProfile.kt
package feature.shop.client.domain

data class UserProfile(
    val user: User,
    val memberships: List<Membership>,
    val permissions: Set<Permission>,
)
```

---

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
