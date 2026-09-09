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

A read whose result has lifecycle states returns a sealed domain model; each variant carries the values that state requires, so no consumer checks nullable properties against Booleans.

```kotlin
sealed interface OrderState {
    val id: OrderId

    data class Draft(override val id: OrderId, val lines: List<OrderLine>) : OrderState
    data class Submitted(override val id: OrderId, val lines: List<OrderLine>, val payment: Payment) : OrderState
    data class Cancelled(override val id: OrderId, val reason: String) : OrderState
}

fun interface GetOrderState {
    suspend operator fun invoke(id: OrderId): OrderState?
}
```

The [Repository examples](serverdata.md#repository) show the consumer before and after the Repository assembles such a model.
