A Repository that exposes domain interfaces as `public val` properties, backed by a Service and local storage:

```kotlin
internal class UserRepository(
    private val userService: UserService,
    private val userStorage: UserStorage, // Local storage
) {
    val getUser = GetUser { id ->
        userService.getUser(UserService.GetUser.Request(id)).user
    }

    val deleteUser = DeleteUser { id ->
        userService.deleteUser(UserService.DeleteUser.Request(id))
    }
}
```

A domain model that draws on local storage from two sources is assembled here, behind one property, rather than exposed as one interface per source:

```kotlin
    val flowOfCheckout = FlowOfCheckout { cartId ->
        combine(
            cartStorage.observe(cartId),
            shippingPreferenceStorage.observe(),
        ) { cart, preference ->
            Checkout(
                items = cart.items.map { it.toDomain() },
                shipping = preference?.toDomain(), // null: no preference chosen yet
            )
        }
    }
```
