# The `data` layer

The `data` axis is **client-only**: Repository implementations and client-side local persistence (Keychain, SharedPreferences, etc.). Server-side persistence and service implementations live in the `services` axis — the server has no `data.*` package (see [the `services` layer](services.md)). Repositories fan out across [Services](services.md#services-the-cross-the-wire-contract) (the `:api` contract) and client-side local storage, and expose [domain interfaces](domain.md#domain-interfaces) for the rest of the feature to consume.

## Layer rules

These apply across the whole `feature.[name].data` package:

{{rules:DataLayer}}

## Repositories

* **Definition**: A class that provides implementations for [domain interfaces](domain.md#domain-interfaces), providing the "edge" of the domain layer.
{{construct:DataLayer.Repository}}
* **Note**: The property name must match the interface name using `lowerCamelCase` (e.g., `val createUser = CreateUser { ... }`).
* **Example**:
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

## Non-Repository client data abstractions

* **Definition**: A client-side interface or class declared in `..data..` that is **not** a Repository — typically a low-level concern with platform-specific actuals (e.g., `BinaryUploadClient` for chunked file upload). Modelled by two constructs:
{{construct:DataLayer.ClientDataInterface}}
{{construct:DataLayer.ClientDataImplementation}}
* **Note**: These exist to give Repositories a clean abstraction over a concrete platform capability. If you find yourself writing one, ask whether it belongs in `:platform:client` instead — feature-local data abstractions are appropriate when the contract is feature-specific.

## Client-side Storage classes (`data.storage`)

* **Definition**: A class responsible for local-device data persistence and retrieval (e.g., credentials, preferences, cached data on disk) — `expect`/`actual` `Storage` classes backed by Keychain (iOS), SharedPreferences (Android), DataStore, etc.
{{construct:DataLayer.ClientStorage}}
* **Note**: Client-side Storage classes may be `expect`/`actual` classes when the underlying storage mechanism is platform-specific (e.g., Keychain on iOS, SharedPreferences on Android).
* **Example**:
```kotlin
// commonMain
expect class AuthCredentialStorage() {
    val authCredentials: StateFlow<AuthCredentials?>
    fun setAuthCredentials(authCredentials: AuthCredentials?)
}

// androidMain
actual class AuthCredentialStorage actual constructor() {
    // Android-specific implementation using SharedPreferences/DataStore
}
```
