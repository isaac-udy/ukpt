`feature.ukpt`'s domain interfaces are `fun interface`s with an `operator fun invoke` primary function — a plain value-returning one plus three `FlowOf…` variants (the `FlowOf` name prefix is required whenever the primary function returns a `Flow`):

```kotlin
// feature.ukpt.domain (:api)
fun interface GetGreeting {
    suspend operator fun invoke(): String
}

fun interface FlowOfGreetings {
    operator fun invoke(): Flow<Greeting>
}

fun interface FlowOfLatestGreeting {
    operator fun invoke(): Flow<Greeting?>          // nullable element inside the wrapper
}

fun interface FlowOfGreetingHistory {
    operator fun invoke(): Flow<List<Greeting>>     // a stream of a collection of domain objects
}
```

Patterns the base template's interfaces don't exercise yet, illustrated — errors via `@Throws` (a `suspend` `@Throws` must include `CancellationException`, or kotlinc rejects the function on iOS), a no-value primary function, a `StateFlow` return, a nested-type parameter, and default convenience functions (which implementations must never override):

```kotlin
fun interface CreateUser {
    @Throws(UserAlreadyExistsException::class, CancellationException::class)
    suspend operator fun invoke(name: String): User

    class UserAlreadyExistsException : RuntimeException()
}

fun interface DeleteUser {
    @Throws(UserNotFoundException::class, CancellationException::class)
    suspend operator fun invoke(userId: String)     // no return value
}

fun interface FlowOfCurrentUser {
    operator fun invoke(): StateFlow<User?>          // StateFlow is a supported reactive wrapper
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

    sealed interface Input {
        data object AllUsers : Input
        data class NameContains(val searchTerm: String) : Input
    }
}

class UserNotFoundException : RuntimeException()
```
