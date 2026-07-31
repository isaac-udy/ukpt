> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/clientdata/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Client Data](../src/main/kotlin/architecture/rules/clientdata/ClientData.kt)

`feature.[name].client.data` — Repository implementations and client-side local persistence
(Keychain, SharedPreferences, etc.). The client's outer edge, and the mirror of
[`server.data`](serverdata.md): a [Repository](#repository) provides
[domain interfaces](clientdomain.md#domain-interface) for the rest of the client to consume, the
way a [server Repository](serverdata.md#repository) provides
[server domain interfaces](serverdomain.md#domain-interface). The two carry the same name because
they are the same construct on opposite sides; what differs is what they read through — a
[Service](serverservices.md#service-interface) and local storage here, a
[StorageClass](serverdata.md#storage-class) over a table there.

This is also the only layer that may talk to the server: Repositories call
[Services](serverservices.md#service-interface) — the `:api` contract — to reach it
(`ClientData.clientServerDependencyRestriction`).

##### Constructs

* [Repository](#repository)
* [Client Data Interface](#client-data-interface)
* [Client Data Implementation](#client-data-implementation)
* [Client Storage](#client-storage)

##### Rules

* The `client.data` layer must provide implementations of `client.domain` interfaces by exposing them as properties, not by inheriting them
    * **Note:** A Repository that implements a domain interface, or fails to expose one as a `public val`, fails the enforcing rules directly.
    * **Enforced by:** `ClientData.Repository.doesNotImplementDomainInterfaces`, `ClientData.Repository.exposesDomainInterfacesAsProperties`
* A `client.data` class must not inject `domain` interfaces; logic that requires multiple domain interfaces belongs in a UseCase
    * **Why:** Repositories implement domain interfaces. If one injects a domain interface, it is calling a sibling Repository through the abstract layer, which makes the dependency graph unreadable and easy to cycle. Logic that needs multiple domain interfaces belongs in a UseCase.
* A `client.data.storage` class must use `internal` visibility where the language allows (see `ClientData.ClientStorage.internalVisibility`)
    * **Enforced by:** `ClientData.ClientStorage.internalVisibility`
* The `client.data` layer must not depend on the `ui` package
    * **Why:** UI is the outermost layer; `client.data` sits beneath it and supplies the domain interfaces the UI consumes. If it imports a UI type the layering becomes circular and the Repository can no longer be tested without a Compose runtime.
* `client.data` is the only client package that may import a `server.services` contract, and client code must not import any other server code
    * **Why:** The network is the single connection between the two sides, and `client.data` is the layer that uses it. A ViewModel that imports a service contract has bypassed the abstraction that makes it testable and swappable; a `client.domain` file that does has stopped being pure.  The contract only — never a ServiceImpl, never `server.data`, never `server.domain`. What the client may see is exactly what the server publishes to `:api` as its wire surface.
    * **Note:** The population is every feature file the client compiles — everything that is not server-private, meaning not in a `server.**` package and not on a `:server` module.
    * **Note:** A feature root file on a `:client` module is the feature's DI module, whose job is to bind a urpc client and therefore to name the contract; roots are governed by `FeatureRules` and are out of scope here.
    * **Note:** Tested over `feature.[name].server.**` imports: the contract is `feature.[name].server.services.**`, declared in `:api` so both sides see it, and everything else under `server.` is the server's own business.
* A `client.data` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Note:** `client.data.storage` is the exception and is visible from anywhere in this layer: it is the local-persistence half of the layer rather than a subsystem.
    * **Enforced by:** `ProjectRules.subsystemVisibility`
* A `client.data` subsystem package imports `client.domain` only through its mirror subsystem, that subsystem's direct children, and their ancestors
    * **Note:** A [Repository](#repository) at the layer root provides root-declared contracts, unconstrained by the mirror; a mirrored `client.data.[sub]` package provides that subsystem's.
    * **Enforced by:** `ProjectRules.subsystemMirrorsDomain`

---

## [Repository](../src/main/kotlin/architecture/rules/clientdata/Repository.kt)

A class that provides implementations for [domain interfaces](clientdomain.md#domain-interface) by
exposing them as `public val` properties. The client's half of a pair: the server states the same
construct as a [server Repository](serverdata.md#repository).

* **Note:** The property name must match the interface name in `lowerCamelCase`, such as
  `val createUser = CreateUser { ... }`.

##### Requirements

* A Repository resides in `feature..client.data..`
* A Repository is a class
* A Repository is named `[Name]Repository`
* A Repository is declared in a file matching its name

##### Rules

* A Repository must be `internal`
    * **Why:** Callers depend on the domain interfaces it provides, never on the Repository itself; `internal` is what makes that the only reachable surface.
* A Repository must not implement domain interfaces directly
    * **Why:** Inheriting the interface makes one class *be* many contracts, so its surface can only grow; exposing them as properties keeps each contract separately nameable and separately injectable.
* A Repository must expose domain interfaces as `public val` properties
    * **Why:** The property name is the interface name in lowerCamelCase, so the wiring reads as a list of the contracts this Repository answers.
* A Repository must not inject domain interfaces
    * **Why:** A Repository that injects a contract is calling a sibling adapter through the abstract layer, which makes the graph unreadable and easy to cycle. Logic that needs several interfaces is a UseCase.
* A Repository must not inject other Repositories
    * **Why:** Two Repositories over one domain object have no single edge, and the second reaches its data through the first's mapping rather than through the source that owns it.
* A Repository's domain-interface properties must be initialized immediately: no `by lazy`, no custom getter
    * **Why:** Eager initialisation lets Koin's graph validation catch a missing or cyclic dependency at startup rather than at first use, and it makes the wiring obvious from a quick read of the constructor.

##### Guidance

* A Repository may inject Services, `client.data.storage` Storage objects, or database clients to fulfill its domain properties

##### Examples

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

---

## [Client Data Interface](../src/main/kotlin/architecture/rules/clientdata/ClientDataInterface.kt)

A client-side interface declared in `feature.[name].client.data` (but not `client.data.storage`)
that is not a Repository. Typically the contract for a low-level concern with platform-specific actuals,
such as `BinaryUploadClient` for chunked file upload.

* **Note:** These exist to give Repositories a clean abstraction over a concrete platform
  capability. If you find yourself writing one, ask whether it belongs in `:platform:client`
  instead; a feature-local data abstraction is appropriate when the contract is
  feature-specific.

##### Requirements

* A Client Data Interface resides in `feature..client.data..`
* A Client Data Interface is an interface
* A Client Data Interface resides in `feature.[name].client.data` (not `client.data.storage`)

---

## [Client Data Implementation](../src/main/kotlin/architecture/rules/clientdata/ClientDataImplementation.kt)

A client-side class in `feature.[name].client.data` (but not `client.data.storage`) that is not
a Repository. Usually a platform-specific implementation of a
[client data interface](#client-data-interface).

##### Requirements

* A Client Data Implementation resides in `feature..client.data..`
* A Client Data Implementation is a class
* A Client Data Implementation is not named `[Name]Repository`
* A Client Data Implementation resides in `feature.[name].client.data` (not `client.data.storage`)

---

## [Client Storage](../src/main/kotlin/architecture/rules/clientdata/ClientStorage.kt)

A class responsible for local-device data persistence and retrieval, such as credentials,
preferences, or cached data on disk. Backed by Keychain (iOS), SharedPreferences (Android),
DataStore, etc.

* **Note:** A Storage class may be an `expect`/`actual` class when the underlying storage
  mechanism is platform-specific, such as Keychain on iOS and SharedPreferences on Android.

##### Requirements

* A Client Storage resides in `feature..client.data..`
* A Client Storage is a class
* A Client Storage is named `[Name]Storage`
* A Client Storage is not abstract
* A Client Storage is not a `data class`
* A Client Storage resides in `feature.[name].client.data.storage`

##### Rules

* A Storage class must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)
    * **Note:** The test skips `expect`/`actual` declarations: an `actual`'s visibility must match its `expect`, so the language decides there, not this Rule.
* A Storage class must not inject domain interfaces, Repositories, or Services
    * **Why:** Storage is the lowest layer of the stack: it should depend on the database or keychain client and nothing higher. Injecting a domain interface, Repository, or Service would embed orchestration logic in the persistence layer.

##### Examples

An `expect`/`actual` Storage class with a platform-specific backing store:

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
