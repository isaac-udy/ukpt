Domain interfaces showing `@Throws` exceptions, `Flow` returns (the `FlowOf` prefix), and default convenience functions:

```kotlin
fun interface CreateUser {
    @Throws(UserAlreadyExistsException::class, CancellationException::class)
    suspend operator fun invoke(name: String): User

    class UserAlreadyExistsException : RuntimeException()
}

fun interface DeleteUser {
    @Throws(UserNotFoundException::class, CancellationException::class)
    suspend operator fun invoke(userId: String)
}

fun interface FlowOfCurrentUser {
    operator fun invoke(): StateFlow<User?>
}

fun interface FlowOfUser {
    @Throws(UserNotFoundException::class)
    operator fun invoke(userId: String): Flow<User>

    fun orNull(userId: String): Flow<User?> {
        return invoke(userId)
            .map { it as User? }
            .catch { ex ->
                if (ex is UserNotFoundException) {
                    emit(null)
                } else {
                    throw ex
                }
            }
    }
}

fun interface FlowOfUsers {
    operator fun invoke(params: Input): Flow<List<User>>

    fun allUsers(): Flow<List<User>> {
        return invoke(Input.AllUsers)
    }

    fun nameContains(searchTerm: String): Flow<List<User>> {
        return invoke(Input.NameContains(searchTerm = searchTerm))
    }

    fun isFriendOf(userId: String): Flow<List<User>> {
        return invoke(Input.FriendOf(userId = userId))
    }

    sealed interface Input {
        data object AllUsers : Input
        data class NameContains(val searchTerm: String) : Input
        data class FriendOf(val userId: String) : Input
    }
}

class UserNotFoundException : RuntimeException()
```

---

A domain interface that returns a computed read projection, combining several sources into one consistent snapshot. Compose in a UseCase when the combination is read-model logic; compose in a Repository when it is one data source's atomic projection. Do not build one projection holding unrelated optional facilities — live polling and optional resources may stay separate when their failure should not make the screen unusable.

```kotlin
fun interface FlowOfUserProfile {
    operator fun invoke(userId: User.Id): Flow<UserProfile>
}
```

---

Reads that one consumer always uses together, before: one interface per fact, and the consumer joins them.

```kotlin
fun interface FlowOfCheckoutItems {
    operator fun invoke(cartId: CartId): Flow<List<CartItem>>
}

fun interface FlowOfCheckoutShipping {
    operator fun invoke(cartId: CartId): Flow<ShippingOption?>
}

fun interface FlowOfCheckoutTotal {
    operator fun invoke(cartId: CartId): Flow<Money>
}
```

After: one read projection returned by the Repository that owns the cart, and the three interfaces above are gone.

```kotlin
data class Checkout(
    val items: List<CartItem>,
    val shipping: ShippingOption?, // null: no option chosen yet
    val total: Money,
)

fun interface FlowOfCheckout {
    operator fun invoke(cartId: CartId): Flow<Checkout>
}
```

`FlowOfCheckoutPromotions` stays a separate interface: promotions are optional, polled on their own schedule, and their failure must not fail the checkout.
