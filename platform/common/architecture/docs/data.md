> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Sources: @Describe annotations in the Kotlin catalog in `src/test/kotlin/architecture/rules/data/` (narrative + rules), plus the `*.examples.md` files beside it.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

# [Data Layer](../src/test/kotlin/architecture/rules/data/DataLayer.kt)

The `data` axis is **client-only**: Repository implementations and client-side local persistence
(Keychain, SharedPreferences, etc.). Server-side persistence and service implementations live in
the `services` axis — the server has no `data.*` package (see [the services layer](services.md)).
Repositories fan out across [Services](services.md#service-interface) (the `:api` contract) and
client-side local storage, and expose [domain interfaces](domain.md#domain-interface) for the
rest of the feature to consume.

##### Constructs

* [Repository](#repository)
* [Client Data Interface](#client-data-interface)
* [Client Data Implementation](#client-data-implementation)
* [Client Storage](#client-storage)

##### Rules

* The `data` layer provides implementations of `domain` interfaces — by exposing them as properties, not by inheriting them
    * **Note**: A Repository that implements a domain interface, or fails to expose one as a `public val`, fails the enforcing rules directly.
    * **Enforced by**: `DataLayer.Repository.doesNotImplementDomainInterfaces`, `DataLayer.Repository.exposesDomainInterfacesAsProperties`
* A `data` class must not inject `domain` interfaces — logic requiring multiple domain interfaces must be moved to a UseCase
    * **Why**: Repositories *implement* domain interfaces — if one injects a domain interface, it's calling a sibling Repository through the abstract layer, which makes the dependency graph unreadable and easy to cycle. Logic that needs multiple domain interfaces belongs in a UseCase.
* A `data.storage` class uses `internal` visibility where the language allows (see `DataLayer.ClientStorage.internalVisibility` for the canonical statement, incl. the `expect`/`actual` nuance)
    * **Enforced by**: `DataLayer.ClientStorage.internalVisibility`
* The `data` layer must not depend on the `ui` package
    * **Why**: UI is the outermost layer; `data` sits beneath it and supplies the domain interfaces the UI consumes. If `data` imports a UI type the layering becomes circular and the Repository can no longer be tested without a Compose runtime.

---

## [Repository](../src/test/kotlin/architecture/rules/data/Repository.kt)

A class that provides implementations for [domain interfaces](domain.md#domain-interface),
providing the "edge" of the domain layer.

* **Note**: The property name must match the interface name using `lowerCamelCase`
  (e.g., `val createUser = CreateUser { ... }`).

##### Requirements

* A Repository resides in `feature..data..`
* A Repository is a class
* A Repository is named `[Name]Repository`
* A Repository is declared in a file matching its name

##### Rules

* A Repository must be marked as `internal`
* A Repository must not implement domain interfaces directly
* A Repository must expose domain interfaces as `public val` properties
* A Repository must not inject domain interfaces
    * **Why**: Logic requiring multiple domain interfaces must be moved to a UseCase in the `domain` package.
* A Repository must not inject other Repositories
* A Repository's domain-interface properties must be initialized immediately — no `by lazy`, no custom getter
    * **Why**: Eager initialisation lets Koin's graph validation catch missing or cyclic dependencies at startup instead of at the first injection at runtime, and it makes the wiring obvious from a quick read of the Repository constructor.

##### Guidance

* A Repository may inject Services, client-side `data.storage` Storage objects, or database clients to fulfill its domain properties

##### Examples

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

---

## [Client Data Interface](../src/test/kotlin/architecture/rules/data/ClientDataInterface.kt)

A client-side interface declared in `..data..` (but not `data.storage`) that is **not** a
Repository — typically the contract for a low-level concern with platform-specific actuals
(e.g., `BinaryUploadClient` for chunked file upload).

* **Note**: These exist to give Repositories a clean abstraction over a concrete platform
  capability. If you find yourself writing one, ask whether it belongs in `:platform:client`
  instead — feature-local data abstractions are appropriate when the contract is
  feature-specific.

##### Requirements

* A Client Data Interface resides in `feature..data..`
* A Client Data Interface is an interface
* A Client Data Interface resides in `feature.[name].data` (not `data.storage`)

---

## [Client Data Implementation](../src/test/kotlin/architecture/rules/data/ClientDataImplementation.kt)

A client-side class in `..data..` (but not `data.storage`) that is **not** a Repository —
usually a platform-specific implementation of a [client data interface](#client-data-interface).

##### Requirements

* A Client Data Implementation resides in `feature..data..`
* A Client Data Implementation is a class
* A Client Data Implementation is not named `[Name]Repository`
* A Client Data Implementation resides in `feature.[name].data` (not `data.storage`)

---

## [Client Storage](../src/test/kotlin/architecture/rules/data/ClientStorage.kt)

A class responsible for local-device data persistence and retrieval (e.g., credentials,
preferences, cached data on disk) — `expect`/`actual` `Storage` classes backed by Keychain
(iOS), SharedPreferences (Android), DataStore, etc.

* **Note**: Client-side Storage classes may be `expect`/`actual` classes when the underlying
  storage mechanism is platform-specific (e.g., Keychain on iOS, SharedPreferences on Android).

##### Requirements

* A Client Storage resides in `feature..data..`
* A Client Storage is a class
* A Client Storage is named `[Name]Storage`
* A Client Storage is not abstract
* A Client Storage is not a `data class`
* A Client Storage resides in the `data.storage` package on `:client`

##### Rules

* A Storage class must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)
    * **Note**: The check skips `expect`/`actual` declarations — an `actual`'s visibility must match its `expect`, so the language decides there, not this rule.
* A Storage class must not inject domain interfaces, Repositories, or Services
    * **Why**: Storage is the lowest layer of the stack — it should depend on the database/keychain client and nothing higher. Injecting a domain interface, Repository, or Service would embed orchestration logic in the persistence layer.

##### Examples

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
