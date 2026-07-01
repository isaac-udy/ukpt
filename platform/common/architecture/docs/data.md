> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Narrative sources: the `DataLayer*.md` fragments in `src/test/kotlin/architecture/rules/data/`; structure and rule content come from the rule catalog.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

# The `data` layer

The `data` axis is **client-only**: Repository implementations and client-side local persistence (Keychain, SharedPreferences, etc.). Server-side persistence and service implementations live in the `services` axis — the server has no `data.*` package (see [the `services` layer](services.md)). Repositories fan out across [Services](services.md#services-the-cross-the-wire-contract) (the `:api` contract) and client-side local storage, and expose [domain interfaces](domain.md#domain-interfaces) for the rest of the feature to consume.

## Rules

* Forbidden from injecting `domain` interfaces — logic requiring multiple domain interfaces must be moved to a UseCase
    * **ID**: `DataLayer.noInjectingDomainInterfaces`
    * **Why**: Repositories *implement* domain interfaces — if one injects a domain interface, it's calling a sibling Repository through the abstract layer, which makes the dependency graph unreadable and easy to cycle. Logic that needs multiple domain interfaces belongs in a UseCase.
* Must not depend on the `ui` package
    * **ID**: `DataLayer.noUiDeps`
    * **Why**: UI is the outermost layer; `data` sits beneath it and supplies the domain interfaces the UI consumes. If `data` imports a UI type the layering becomes circular and the Repository can no longer be tested without a Compose runtime.

## Guidance

* Provides implementations of `domain` interfaces — by exposing them as properties, not by inheriting them
    * **ID**: `DataLayer.providesDomainImplementations`
    * **Note**: Enforced via the `DataLayer.Repository` construct's classification: a class that implements a domain interface (or doesn't expose one as a `public val`) isn't recognised as a Repository.
* `data.storage` classes use `internal` visibility where the language allows (see `DataLayer.ClientStorage.internalVisibility` for the canonical statement, incl. the `expect`/`actual` nuance)
    * **ID**: `DataLayer.storageInternalVisibility`

## Repositories

A class that provides implementations for [domain interfaces](domain.md#domain-interfaces), providing the "edge" of the domain layer.
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

**Definition** — a declaration is a `DataLayer.Repository` when it satisfies all of:

* resides in `feature..data..`
* is a class
* name ends with `Repository`
* is declared in a file matching its name

**Rules**:

* Repositories must be marked as `internal`
    * **ID**: `DataLayer.Repository.internalVisibility`
* Repositories must not implement domain interfaces directly
    * **ID**: `DataLayer.Repository.doesNotImplementDomainInterfaces`
* Repositories must expose domain interfaces as `public val` properties
    * **ID**: `DataLayer.Repository.exposesDomainInterfacesAsProperties`
* Repositories are forbidden from injecting domain interfaces
    * **ID**: `DataLayer.Repository.doesNotInjectDomainInterfaces`
    * **Why**: Logic requiring multiple domain interfaces must be moved to a UseCase in the `domain` package.
* Repositories are forbidden from injecting other Repositories
    * **ID**: `DataLayer.Repository.doesNotInjectRepositories`
* Repository domain-interface properties must be initialized immediately — no `by lazy`, no custom getter
    * **ID**: `DataLayer.Repository.propertiesEagerlyInitialized`
    * **Why**: Eager initialisation lets Koin's graph validation catch missing or cyclic dependencies at startup instead of at the first injection at runtime, and it makes the wiring obvious from a quick read of the Repository constructor.

**Guidance**:

* May inject Services, client-side `data.storage` Storage objects, or database clients to fulfill their domain properties
    * **ID**: `DataLayer.Repository.mayInjectServicesStorageOrClients`

## Client data interfaces

A client-side interface declared in `..data..` (but not `data.storage`) that is **not** a Repository — typically the contract for a low-level concern with platform-specific actuals (e.g., `BinaryUploadClient` for chunked file upload).
* **Note**: These exist to give Repositories a clean abstraction over a concrete platform capability. If you find yourself writing one, ask whether it belongs in `:platform:client` instead — feature-local data abstractions are appropriate when the contract is feature-specific.

**Definition** — a declaration is a `DataLayer.ClientDataInterface` when it satisfies all of:

* resides in `feature..data..`
* is an interface
* Must live in `feature.[name].data` (not `data.storage`)

## Client data implementations

A client-side class in `..data..` (but not `data.storage`) that is **not** a Repository — usually a platform-specific implementation of a [client data interface](#client-data-interfaces).

**Definition** — a declaration is a `DataLayer.ClientDataImplementation` when it satisfies all of:

* resides in `feature..data..`
* is a class
* Must not be named `Repository`
* Must live in `feature.[name].data` (not `data.storage`)

## Client-side Storage classes (`data.storage`)

A class responsible for local-device data persistence and retrieval (e.g., credentials, preferences, cached data on disk) — `expect`/`actual` `Storage` classes backed by Keychain (iOS), SharedPreferences (Android), DataStore, etc.
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

**Definition** — a declaration is a `DataLayer.ClientStorage` when it satisfies all of:

* resides in `feature..data..`
* is a class
* name ends with `Storage`
* Storage classes must not be abstract
* Storage classes must not be `data class`
* Storage classes must reside in the `data.storage` package on `:client`

**Rules**:

* Storage classes are forbidden from injecting domain interfaces, Repositories, or Services
    * **ID**: `DataLayer.ClientStorage.doesNotInjectDomainRepositoriesOrServices`
    * **Why**: Storage is the lowest layer of the stack — it should depend on the database/keychain client and nothing higher. Injecting a domain interface, Repository, or Service would embed orchestration logic in the persistence layer.

**Guidance**:

* Storage classes must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)
    * **ID**: `DataLayer.ClientStorage.internalVisibility`
