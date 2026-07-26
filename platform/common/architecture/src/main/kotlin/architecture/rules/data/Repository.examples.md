> **Illustrative.** `:feature:core` ships its domain interfaces implemented directly as UseCases (`GetGreetingImpl`, `FlowOfGreetingsImpl`, …) and no Repository. The code below shows the shape a Repository takes once a feature composes a Service plus local storage behind its domain interfaces.

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
        userStorage.remove(id) // keep local storage in sync with the service
    }
}
```
