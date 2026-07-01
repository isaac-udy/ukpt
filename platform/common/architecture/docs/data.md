<!--
  GENERATED FILE — do not edit.
  Narrative source: src/test/kotlin/architecture/rules/data/DataLayer.md (structured blocks come from the rule catalog).
  Regenerate: UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test
-->

# The `data` layer

The `data` axis is **client-only**: Repository implementations and client-side local persistence (Keychain, SharedPreferences, etc.). Server-side persistence and service implementations live in the `services` axis — the server has no `data.*` package (see [the `services` layer](services.md)). Repositories fan out across [Services](services.md#services-the-cross-the-wire-contract) (the `:api` contract) and client-side local storage, and expose [domain interfaces](domain.md#domain-interfaces) for the rest of the feature to consume.

## Layer rules

These apply across the whole `feature.[name].data` package:

* **`DataLayer.providesDomainImplementations`** `📋 guidance` — Provides implementations of `domain` interfaces — by exposing them as properties, not by inheriting them
    * **Note**: Enforced via the `DataLayer.Repository` construct's classification: a class that implements a domain interface (or doesn't expose one as a `public val`) isn't recognised as a Repository.
* **`DataLayer.noInjectingDomainInterfaces`** `✅ tested` — Forbidden from injecting `domain` interfaces — logic requiring multiple domain interfaces must be moved to a UseCase
    * **Why**: Repositories *implement* domain interfaces — if one injects a domain interface, it's calling a sibling Repository through the abstract layer, which makes the dependency graph unreadable and easy to cycle. Logic that needs multiple domain interfaces belongs in a UseCase.
* **`DataLayer.storageInternalVisibility`** `📋 guidance` — `data.storage` classes use `internal` visibility where the language allows (see `DataLayer.ClientStorage.internalVisibility` for the canonical statement, incl. the `expect`/`actual` nuance)
* **`DataLayer.noUiDeps`** `✅ tested` — Must not depend on the `ui` package
    * **Why**: UI is the outermost layer; `data` sits beneath it and supplies the domain interfaces the UI consumes. If `data` imports a UI type the layering becomes circular and the Repository can no longer be tested without a Compose runtime.

## Repositories

* **Definition**: A class that provides implementations for [domain interfaces](domain.md#domain-interfaces), providing the "edge" of the domain layer.
* **Construct** `DataLayer.Repository` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..data..`
    * is a class
    * name ends with `Repository`
    * is declared in a file matching its name
* **Rules**:
    * **`DataLayer.Repository.internalVisibility`** `✅ tested` — Repositories must be marked as `internal`
    * **`DataLayer.Repository.doesNotImplementDomainInterfaces`** `✅ tested` — Repositories must not implement domain interfaces directly
    * **`DataLayer.Repository.exposesDomainInterfacesAsProperties`** `✅ tested` — Repositories must expose domain interfaces as `public val` properties
    * **`DataLayer.Repository.doesNotInjectDomainInterfaces`** `✅ tested` — Repositories are forbidden from injecting domain interfaces
        * **Why**: Logic requiring multiple domain interfaces must be moved to a UseCase in the `domain` package.
    * **`DataLayer.Repository.doesNotInjectRepositories`** `✅ tested` — Repositories are forbidden from injecting other Repositories
    * **`DataLayer.Repository.propertiesEagerlyInitialized`** `✅ tested` — Repository domain-interface properties must be initialized immediately — no `by lazy`, no custom getter
        * **Why**: Eager initialisation lets Koin's graph validation catch missing or cyclic dependencies at startup instead of at the first injection at runtime, and it makes the wiring obvious from a quick read of the Repository constructor.
    * **`DataLayer.Repository.mayInjectServicesStorageOrClients`** `📋 guidance` — May inject Services, client-side `data.storage` Storage objects, or database clients to fulfill their domain properties
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
* **Construct** `DataLayer.ClientDataInterface` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..data..`
    * is an interface
    * Must live in `feature.[name].data` (not `data.storage`)
* **Construct** `DataLayer.ClientDataImplementation` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..data..`
    * is a class
    * Must not be named `Repository`
    * Must live in `feature.[name].data` (not `data.storage`)
* **Note**: These exist to give Repositories a clean abstraction over a concrete platform capability. If you find yourself writing one, ask whether it belongs in `:platform:client` instead — feature-local data abstractions are appropriate when the contract is feature-specific.

## Client-side Storage classes (`data.storage`)

* **Definition**: A class responsible for local-device data persistence and retrieval (e.g., credentials, preferences, cached data on disk) — `expect`/`actual` `Storage` classes backed by Keychain (iOS), SharedPreferences (Android), DataStore, etc.
* **Construct** `DataLayer.ClientStorage` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..data..`
    * is a class
    * name ends with `Storage`
    * Storage classes must not be abstract
    * Storage classes must not be `data class`
    * Storage classes must reside in the `data.storage` package on `:client`
* **Rules**:
    * **`DataLayer.ClientStorage.internalVisibility`** `📋 guidance` — Storage classes must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)
    * **`DataLayer.ClientStorage.doesNotInjectDomainRepositoriesOrServices`** `✅ tested` — Storage classes are forbidden from injecting domain interfaces, Repositories, or Services
        * **Why**: Storage is the lowest layer of the stack — it should depend on the database/keychain client and nothing higher. Injecting a domain interface, Repository, or Service would embed orchestration logic in the persistence layer.
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

