# Domain interfaces

A functional interface representing domain-level functionality/business logic.
* **Note**: Default functions don't need to be `operator fun invoke` and should use expressive names; they should provide commonly used functionality (e.g. handling a particular exception type) or simplify calling the primary function with particular parameters.
* **Note**: Implementations must never override an interface's default functions; convenience functions belong as default members, not top-level extensions, so they're discoverable and co-located with the interface.
* **Note**: Generic/unknown errors don't need their own exception type or `@Throws` entry.
* **Examples**:
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
        return invoke(UserSearchInput.AllUsers)
    }

    fun nameContains(searchTerm: String): Flow<List<User>> {
        return invoke(UserSearchInput.NameContains(searchTerm = searchTerm))
    }

    fun isFriendOf(userId: String): Flow<List<User>> {
        return invoke(UserSearchInput.FriendOf(userId = userId))
    }

    sealed interface Input {
        data object AllUsers : Input
        data class NameContains(val searchTerm: String) : Input
        data class FriendOf(val userId: String) : Input
    }
}

class UserNotFoundException : RuntimeException()
```
