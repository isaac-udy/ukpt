# UKPT Architecture
This document describes the architecture that should be used for the UKPT project.

## Rule IDs

Every rule in this document has a stable ID of the form `R-<axis>-<NN>` (e.g. `R-UI-12`). The axis prefix groups rules by the layer they govern:

| Prefix | Axis | Sections |
| --- | --- | --- |
| `R-MOD` | Module / Gradle structure | §1, §2 |
| `R-DOM` | Domain | §3.1, §4.1 |
| `R-UI` | UI | §3.2, §4.2 |
| `R-DATA` | Data (client) | §3.3, §4.3 |
| `R-SVC` | Services | §3.4, §4.4 |
| `R-FEAT` | Feature top-level / DI | §3.5, §4.5 |
| `R-PROJ` | Project-wide | §5, §6 |

Test failures, PR comments, and architecture exceptions reference rules by ID. Search this file for an ID (e.g. `R-UI-12`) to find the canonical rule text.

### Enforcement status

Each rule is tagged with how it is enforced, so it's clear which rules are machine-checked and which rely on review:

| Tag | Meaning |
| --- | --- |
| `✅ tested` | A Konsist test enforces this rule directly — usually citing the rule ID in its failure message. Search the test sources for the ID to find it. |
| `🔶 construct` | Enforced **indirectly** via the layer-membership meta-test (`validateXxxLayerPackage`): the rule is encoded as a requirement on a construct definition in [`definitions/`](src/test/kotlin/architecture/definitions). A violation surfaces as a generic "declaration matches no construct" failure rather than a named-rule failure. |
| `📋 guidance` | A convention or behavioural rule that static analysis can't check (e.g. "should…", "accept minimal data", permissive "may…" allowances). Enforced by review, not by a test. |
| `⚙️ codegen` | Guaranteed by the `dev.isaacudy.udytils.postgres` code generator — the shape is generated from the migrated schema, so there is nothing in `src/` for Konsist to scan. |

So "search the codebase for the same ID to find the test" holds for `✅ tested` rules; `🔶 construct` rules are enforced through their construct definition; `📋 guidance` and `⚙️ codegen` rules are not machine-checked in `src/`.

## CI enforcement

Run the full architecture test suite with `--rerun-tasks` so Konsist's project-scope cache is bypassed — that's load-bearing, because Konsist scans the source tree and a stale cache can mask new violations:

```
./gradlew :platform:common:architecture:test --rerun-tasks
```

ukpt does not yet wire this into CI. When you want it enforced automatically, add a workflow (e.g. `.github/workflows/pr-verification.yml`) that runs the command above on pull requests.

## **1. Gradle Project Structure**

The project is organized into three root-level module groups.

### **1.1 `:app` (Application shells)**

* **Purpose**: Final executable entry points and dependency injection (DI) wiring.
* **Structure**: May contain sub-groups (e.g., `:app:admin`, `:app:customer`) if multiple applications are built from the same codebase.
* **Child Modules**: Each app contains a `:client` (Mobile/Desktop/Web) and/or a `:server` (Ktor executable).
    * **Client structure (AGP 9.0)**: Under AGP 9.0 a single Kotlin Multiplatform module can no longer also be a `com.android.application`, so the client is itself a group: a shared KMP library `:app:client:shared` (the `com.android.kotlin.multiplatform.library` plugin) holding the shared UI, navigation, DI wiring, and the iOS framework entry point (`iosMain`), plus thin per-platform application modules `:app:client:android` (`com.android.application`), `:app:client:desktop` (Compose Desktop), and `:app:client:web` (wasmJs). The per-platform modules contain only their entry point + platform packaging and depend on `:app:client:shared`.
* **Constraints**: Must not contain business logic. Limited to infrastructure configuration and DI module aggregation.

### **1.2 `:feature` (Vertical slices of functionality)**

* **Purpose**: Encapsulated feature-specific functionality.
* **Sub-Modules**:
    * **`:api`**: Mandatory. Contains the shared contract.
    * **`:client`**: Optional. Contains UI and client-side logic.
    * **`:server`**: Optional. Contains server-side implementation.
* **Notes**:
    * Small projects may start with a single `:feature:core` containing all feature/domain code. As complexity increases, logic is migrated into specific `:feature:name` modules.
    * When starting with a single `:feature:core` feature module, it is a good idea to "preempt" the migration of `:feature:core` into individual `:feature:[name]` modules by using `feature.[name]` for package names within `:feature:core` (instead of `feature.core`)
      * If you are following this pattern, the named feature packages within `:feature:core` should only depend on other named packages via the api module
      * Example: If `:feature:core` contains `feature.auth` and `feature.invoices`, code in `feature.auth` should only depend on `feature.invoices` code which is in the `:feature:core:api` module
    * `:client` and `:server` modules are optional, but at least one of the two should exist for every feature.

### **1.3 `:platform` (Infrastructure)**

* **Purpose**: Reusable, non-feature-specific capabilities.
* **Sub-Groups**:
    * **`:common`**: Code shared by both client and server (e.g., utilities).
    * **`:client`**: Client-only infrastructure (e.g., Design System, local DB drivers).
    * **`:server`**: Server-only infrastructure (e.g., Ktor plugins, and `:platform:server:postgres` — which owns the Flyway SQL migrations + `schema.sql` and applies the `dev.isaacudy.udytils.postgres` codegen plugin; the DB runtime itself lives in that udytils library).

---

## **2. Gradle Project Dependency Rules**

### **2.1 Feature Constraints**

* **Rule [R-MOD-01]** `✅ tested`: `:feature` modules must never depend on `:app` modules.
* **Rule [R-MOD-02]** `📋 guidance`: `:feature` modules may depend on `:platform` modules.
* **Rule [R-MOD-03]** `✅ tested`: `:feature:[name]:client` modules must never depend on any other `:client` or `:server` module
* **Rule [R-MOD-04]** `✅ tested`: `:feature:[name]:client` modules may depend on any `:feature:[name]:api` module
* **Rule [R-MOD-05]** `✅ tested`: `:feature:[name]:server` modules must never depend on any other `:client` or `:server` module
* **Rule [R-MOD-06]** `✅ tested`: `:feature:[name]:server` modules may depend on any `:feature:[name]:api` module
* **Rule [R-MOD-07]** `📋 guidance`: `:feature:[name]:api` modules may depend on another `:feature:[name]:api` module to share models or interfaces.
    * **Note**: `:api` to `:api` dependencies are allowed, but should be used sparingly, treated with caution, and minimised where possible.
* **Rule [R-MOD-08]** `📋 guidance`: `:feature` modules may be grouped (e.g. `:feature:[group]:[name]:api/client/server`)
    * **Note**: A module that serves as a group should exist only as a group, and should not itself contain `:api`, `:server` or `:client` modules.

### **2.2 Platform Constraints**
* **Rule [R-MOD-09]** `✅ tested`: `:platform` modules must never depend on `:app` modules.
* **Rule [R-MOD-10]** `✅ tested`: `:platform` modules must never depend on `:feature` modules.
* **Rule [R-MOD-11]** `📋 guidance`: `:platform` modules may depend on other `:platform` modules.
    * **Note**: `:platform` to `:platform` dependencies are allowed, but should be used sparingly, treated with caution, and minimised where possible.

---

## **3. Feature Architecture (Package level)**

Every feature module follows a strict package hierarchy: `feature.[name].[package]`.

The top-level package `feature.[name]` is also used for dependency injection wiring.

All sub-packages may include subpackages for grouping. For example, a `..ui` package in a feature that includes both list and detail functionality may have `feature.[name].ui.list` and `feature.[name].ui.detail`. This same pattern applies for the domain and data packages.

### **3.1 `domain` package (in `:api`, `:client`, and `:server`)**

* **Contents**: Pure Kotlin Data Models and single-function interfaces (Interactors).
* **Rule [R-DOM-01]** `✅ tested`: Must not contain any platform-specific dependencies (e.g., Android, Ktor, SQL).
* **Rule [R-DOM-02]** `✅ tested`: Must not depend on `ui` or `data` packages within the feature.
* **Rule [R-DOM-03]** `✅ tested`: May depend on another feature's `domain` package, but only if the dependency is on code defined in the `:api` module of the other feature. This is enforced by the general cross-feature dependency rule (see [2.1](#21-feature-constraints)).
    * **Note**: Cross-feature domain dependencies should be minimised where possible, but are permitted because real-world domains have genuine dependencies between them. The important thing is getting the direction of dependencies correct and avoiding circular dependencies.

### **3.2 `ui` package (in `:api` and `:client`)**

* **`:api` Contents**: Serializable Navigation Keys.
* **`:client` Contents**: Compose UI, ViewModels, and UI-state models.
* **Rule [R-UI-01]** `📋 guidance`: May depend on `domain`.
* **Rule [R-UI-02]** `✅ tested`: Forbidden from *implementing* `domain` interfaces.
* **Rule [R-UI-03]** `✅ tested`: Forbidden from depending on `data` or `services`. Calling the server goes through Repositories (in `data`), which expose [Domain Interfaces](#411-domain-interfaces) for the UI to consume.

### **3.3 `data` package (in `:client` only)**

* **Contents**: Repository implementations and client-side local persistence (Keychain, SharedPrefs, etc.).
* **Rule [R-DATA-01]** `🔶 construct`: Provides implementations of `domain` interfaces — by *exposing* them as properties, **not** by inheriting them (see R-DATA-06, R-DATA-07).
* **Rule [R-DATA-02]** `✅ tested`: Forbidden from *injecting* `domain` interfaces. Logic requiring multiple domain interfaces must be moved to a UseCase in the `domain` package.
* **Note**: The `data` axis is **client-only**. Server-side persistence is in `services.storage` (see 3.4); the server has no `data.*` package.

#### **3.3.1 `data.storage` package (in `:client` only)**

* **Contents**: Client-side local persistence types — `expect`/`actual` `Storage` classes backed by Keychain (iOS), SharedPreferences (Android), DataStore, etc.
* **Rule [R-DATA-03]** `📋 guidance`: `internal` visibility where the language allows — see R-DATA-14 for the canonical statement (incl. the `expect`/`actual` nuance).

### **3.4 `services` package (in `:api` and `:server`)**

The `services` axis defines the contract that crosses the wire between client and server. The contract lives in `:api` (so both sides see it); the server-side implementation lives in `:server` under the same package name (dual-life).

`services` is **not** a UI-equivalent outer layer — it sits *parallel* to the `data` axis and is consumed by it. On the client, Repositories (in `data`) inject Service contracts to call the server. On the server, `services` is where the request-handling implementation lives, and reaches down into `services.storage` for persistence and `services.internal.*` for sub-tasks.

The cross-the-wire mechanism is **urpc** (`dev.isaacudy.udytils:urpc-*`): a service is an `@Urpc` interface, and KSP generates the client, the server binding, and the wire descriptors. See [§4.4.1](#441-services-the-cross-the-wire-contract).

* **`:api` Contents**: Service interface contracts. A service interface is an `interface` annotated `@Urpc` whose functions are plain `suspend fun`/`Flow`-returning methods; each function's `Request`/`Response` types are declared with the service (nested `@Serializable` types under a per-function `object` namespace).
* **`:server` Contents**: `[Name]ServiceImpl` classes implementing the contract.
* **Rule [R-SVC-01]** `✅ tested`: `services` may depend on `domain` and on other features' `:api`-defined `services` contracts. It must not depend on `data` (the server has no `data`; the client's `data` depends on `services`, not the other way around).

#### **3.4.1 `services.internal` package (in `:server`)**

Server-side coordinator and helper classes — the things that do the work the ServiceImpl orchestrates.

* **Bare `services.internal`**: top-level orchestrators (e.g. `SessionProcessingManager`) that compose multiple subsystems.
* **`services.internal.<subsystem>`**: subsystem packages. Each direct child of `services.internal` is a sealed island under hierarchical visibility (see 3.4.5).

#### **3.4.2 `services.storage` package (in `:server`)**

Server-side Postgres persistence built on **Exposed** and the **`dev.isaacudy.udytils.postgres`** runtime: `[Name]Storage` classes, `Row ↔ Domain` mapping functions, and codec objects. The generated Exposed `Table` objects and `XxxRow` data classes do **not** live here — they are generated into the shared `platform.server.postgres.tables` package and imported by each feature's storage code. Full details in [§4.4.4](#444-servicesstorage-package--postgres-persistence).

#### **3.4.3 `services.tools` package (in `:server`, reserved)**

Reserved for AI tool-use subclasses. ukpt has no AI subsystem, so this package is intentionally empty (see [§4.4.5](#445-servicestools-package-reserved)).

* **Rule [R-SVC-02]** `✅ tested`: Anything placed in `services.tools` may depend on the Service contract via `:api`-defined types only — never on `services.storage` or `services.internal`.

#### **3.4.4 Cross-axis dependency rules**

Within a feature:

* `domain` may not depend on any other axis. It is the deepest layer.
* `services` may depend on `domain`.
* `data` (client only) may depend on `domain` and on `services` contracts (so Repositories can call the server).
* `ui` (client only) may depend on `domain` only. It must not depend on `data` or `services` directly — calling the server goes through Repositories, which expose [Domain Interfaces](#411-domain-interfaces) for the UI to consume.
* No axis may depend on `ui`.

Reading these as a directed graph:

* On the client: `ui → domain ← services ← data` (and `data → domain`).
* On the server: `domain ← services` (with `services` reaching internally into `services.storage` and `services.internal`).

`domain` is the centre of gravity on both sides. `services` is a sibling of `data` (not an outer shell above it) — the wire-crossing contract that `data` consumes on the client and `services` itself implements on the server.

#### **3.4.5 Hierarchical visibility within `services.internal`**

Inside `feature.[name].services.internal.**`, an import is allowed only if it points to:

* the **same package**, or
* a **descendant** package, or
* an **ancestor** package, **and only when the imported declaration is a pure data shape**.

Lateral / cousin imports are forbidden outright. Ancestor imports of behaviour-bearing types (regular classes, regular interfaces, top-level functions, objects with member functions) are forbidden too — those would let a subsystem reach back up to *invoke* its parent or use behaviour from a higher level, which re-introduces the cross-subsystem coupling the rule is designed to prevent.

The carve-out for data shapes lets the orchestrator-mediated composition pattern work: a payload type that flows from one subsystem through the orchestrator into another can live at a common ancestor (typically bare `services.internal`), and both subsystems may name it without invoking any behaviour.

A "data shape" is any of:
* `data class`, `enum class`, `value class`, `data object`,
* `sealed class` / `sealed interface`,
* an `object` that holds only `val` constants (no functions).

Each direct child of `services.internal` is a sealed island. The bare `services.internal` package is where the orchestrator (and the shared-payload data types) live. A subsystem may subdivide into deeper subpackages — the rule applies recursively, so each new subpackage inherits the same sealing rules.

Slogan: *"You can see your children freely, your parents only for shared data, and never your siblings."*

### **3.5 top-level package (in `:client` and `:server`)**
* **Contents**: Dependency injection modules which define dependency injection bindings. The top-level feature package is reserved for DI wiring — concrete classes (ServiceImpls, helpers) live in their layer-specific package.

---

## **4. Feature Architecture (Code level)**

Within the packages of a feature module, every class, function or other code-level construct is defined as a component in the architecture, based on it's responsibilities and package location.

### **4.1 `domain` package constructs**

The `domain` package must only contain [domain interfaces](#411-domain-interfaces), [domain objects](#412-domain-objects), [UseCases](#413-usecases), [domain extension functions](#414-domain-extension-functions), and exceptions. Every class must fall into one of these categories, and every interface must be either a `fun interface` (domain interface) or a `sealed interface` (domain object).

#### **4.1.1 domain interfaces**
* **Definition**: A functional interface representing domain-level functionality/business logic
* **Rule [R-DOM-04]** `🔶 construct`: Domain interfaces must be a `fun interface`
* **Rule [R-DOM-05]** `🔶 construct`: The primary function of a domain interface must be an `operator fun invoke`
* **Rule [R-DOM-06]** `📋 guidance`: Domain interfaces may define additional default functions that call the primary function
    * **Note**: Default functions in a domain interface do not need to be `operator fun invoke` functions, and should aim to use expressive names
    * **Note**: Default functions in a domain interface should aim to provide commonly used functionality (e.g. handling of a particular exception type), or to simplify calling the domain interface's primary function with particular parameters
    * **Note**: Implementations of a domain interface must never override the default functions belonging to a domain interface
    * **Note**: Convenience functions for domain interfaces must be defined as default member functions, not as top-level extension functions, so that they are discoverable and co-located with the interface definition
* **Rule [R-DOM-07]** `🔶 construct`: All functions in a domain interface must either be `suspend` functions or return a `Flow<T>`
    * **Note**: Domain interfaces that return a `Flow<T>` in their primary function should be prefixed with `FlowOf`
    * **Note**: Domain interfaces functions that return a `Flow<T>` may also return the `StateFlow<T>` subtype of `Flow<T>`
* **Rule [R-DOM-08]** `📋 guidance`: The parameters of the primary function of a domain interface must be [domain objects](#412-domain-objects), nested types belonging to the domain interface, primitive types, or collections of the preceding
* **Rule [R-DOM-09]** `📋 guidance`: The primary function of a domain interface must return [domain objects](#412-domain-objects), nested types belonging to the domain interface, primitive types, collections of the preceding or no value
* **Rule [R-DOM-10]** `✅ tested`: Domain interface functions must expect errors to be propagated using thrown exceptions; a domain interfaces return type should only ever represent a successful result.
    * **Note**: Known exceptions should be defined as their own type that extends `RuntimeException`, either at the top-level within the domain package (in the case of exceptions that are thrown by multiple domain interfaces) or as nested class within the domain interface (in the case of exceptions that are thrown by a specific domain interface)
    * **Note**: Known exceptions should be marked on the domain interface's function using the `@Throws` annotation
    * **Note**: `@Throws` annotations on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException` (or a superclass such as `Exception`) in the exception list, as this is required for Kotlin/Native compilation
    * **Note**: Domain interface functions may also expect generic/unknown errors (e.g. RuntimeException) to occur, but these do not need to be defined as their own type or marked in `@Throws`
* **Rule [R-DOM-11]** `📋 guidance`: Domain interfaces must be implemented by a [Repository](#431-repositories) (as a property of the Repository) or by a [UseCase](#413-usecases)
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

#### **4.1.2 domain objects**
* **Definition**: An immutable type representing data at the domain-level
* **Rule [R-DOM-12]** `🔶 construct`: Domain objects must be immutable (val properties only)
* **Rule [R-DOM-13]** `📋 guidance`: Domain objects should use nested value classes for identifiers (e.g. value class Id(val value: String)) where appropriate
* **Rule [R-DOM-14]** `📋 guidance`: Domain objects should use sealed interface hierarchies to model polymorphic data where appropriate
* **Rule [R-DOM-15]** `🔶 construct`: Domain objects must be annotated with `@Serializable`
* **Rule [R-DOM-16]** `📋 guidance`: Domain objects should include `init` blocks that enforce invariants (rules that must be true for the object to be considered "valid")
* **Rule [R-DOM-17]** `📋 guidance`: Domain objects should use nested types (enums, value classes, sealed interfaces/classes) if the nested objects are conceptually inseparable from the parent domain object, otherwise these types should be defined as their own domain objects
* **Examples**:
    ```kotlin
    @Serializable
    data class User(
        val id: Id,
        val name: String,
        val friends: List<Id>,
    ) {
        @Serializable
        @JvmInline
        value class Id(val value: String)
    }
  
    @Serializable
    data class UserAndFriends(
        val user: User,
        val friends: List<User>,
    ) {
        init {
            require(friends.all { friend -> user.friends.contains(friend.id) }) {
                "All users in friends must have an id matching a value in user.friends"
            }
        }
    } 
  
    @Serializable
    sealed interface Transport {
        val id: String
        val name: String
  
        @Serializable
        data class Car(
            override val id: String,
            override val name: String,
            val fuelType: FuelType,
        ) {
            @Serializable
            enum class FuelType {
                Petrol,
                Diesel,
                Electric,
                Hydrogen,
            }
        }     
        
        @Serializable
        data class Bicycle(
            override val id: String,
            override val name: String,
            val type: Type,
        ) {
            @Serializable
            enum class Type {
                Manual,
                Electric,
            }
        } 

        @Serializable
        data class Bus(
            override val id: String,
            override val name: String,
            val routeId: String,
        )
    }
    ```

#### **4.1.3 UseCases**
* **Definition**: A class that implements a single domain interface
* **Rule [R-DOM-18]** `🔶 construct`: A UseCase must implement exactly one domain interface
* **Rule [R-DOM-19]** `✅ tested`: A UseCase must not override any of the default functions provided by the domain interface
* **Rule [R-DOM-20]** `📋 guidance`: A UseCase may inject domain interfaces to perform its logic
    * **Note**: If a UseCase only injects a single other domain interface to perform it's logic, consider whether it makes sense for that logic to become a default function of the other domain interface
* **Rule [R-DOM-21]** `🔶 construct`: A UseCase must not contain mutable state — all properties must be `val`. Immutable helper properties (e.g., loggers) are permitted.
* **Rule [R-DOM-22]** `📋 guidance`: If UseCase logic becomes too complex, consider breaking the UseCase down into smaller parts; these could be file-private extension functions, private functions within the UseCase, or nested classes within the UseCase
    * **Note**: Avoid adding additional domain interfaces/UseCases to reduce complexity in a UseCase; these domain interfaces/UseCases often end up being used only by a single UseCase, but pollute the domain namespace and add complexity to the dependency graph

#### **4.1.4 domain extension functions**
* **Definition**: A top-level extension function on a domain object that adds derived or convenience behavior
* **Rule [R-DOM-23]** `🔶 construct`: The receiver type, return type, and all parameter types must be domain objects, primitive types, or collections of the preceding
* **Rule [R-DOM-24]** `📋 guidance`: Domain extension functions must not introduce platform-specific dependencies
* **Note**: Prefer default member functions on domain interfaces for domain interface-related convenience logic (see [4.1.1](#411-domain-interfaces)). Extension functions are appropriate for adding behavior to domain objects (e.g., `CampaignRole.permissions()`)

#### **4.1.5 domain extension properties**
* **Definition**: A top-level extension property on a domain object that exposes derived state
* **Rule** `🔶 construct`: The receiver type and property type must be domain-compatible (domain objects, primitives, or collections of the preceding)
* **Rule** `📋 guidance`: Domain extension properties must not introduce platform-specific dependencies
* **Note**: Same constraints as [domain extension functions](#414-domain-extension-functions). Prefer a property when the value is a pure projection of the receiver and is cheap to compute on every read.

#### **4.1.6 domain exceptions**
* **Definition**: A class that represents a known failure mode raised by a domain interface
* **Rule** `🔶 construct`: Domain exceptions must extend `RuntimeException`, `Exception`, or `PresentableException`
* **Rule** `📋 guidance`: Domain exceptions live at the top of the `domain` package when shared between multiple domain interfaces, or as a nested class on the domain interface that throws them (see [4.1.1](#411-domain-interfaces))
* **Rule** `📋 guidance`: Domain exceptions thrown by a domain interface must be listed in `@Throws` on the interface's primary function

#### **4.1.7 domain constants**
* **Definition**: An `object` declaration whose only members are `val` constants — used to anchor domain-level magic numbers, lookup tables, or named tags
* **Rule** `🔶 construct`: Must contain only `val` properties; no functions, no mutable state
* **Note**: A constants object is the right home for things like `val MAX_PARTY_SIZE = 6` or a sealed-but-keyed lookup table. Anything that wants behaviour belongs on a domain object as a member or extension.

### **4.2 `ui` package constructs**
#### **4.2.1 Screens**
* **Definition**: A Composable function that defines the layout and visual representation of a feature or portion of a feature
* **Rule [R-UI-04]** `🔶 construct`: Screen functions must be annotated with `@Composable`
* **Rule [R-UI-05]** `🔶 construct`: Screen functions must be bound to the Destination associated with the Screen (e.g. `[Name]Destination`) using the `@NavigationDestination` annotation
* **Rule [R-UI-06]** `🔶 construct`: Screen functions must be named `[name]Screen`
* **Rule [R-UI-07]** `🔶 construct`: Screen functions must have a single parameter, which is the ViewModel associated with the Screen (e.g. `[Name]ViewModel`)
* **Rule [R-UI-08]** `📋 guidance`: Screen functions have a 1:1 relationship with a [ViewModel](#423-viewmodels) and [ViewModel State](#424-viewmodel-state)
* **Rule [R-UI-09]** `📋 guidance`: Screen functions must observe the ViewModel's `state` property and use this to drive the state of the UI
* **Rule [R-UI-10]** `📋 guidance`: Screen functions should delegate all user interaction handling to the ViewModel
* **Rule [R-UI-11]** `✅ tested`: Screen functions must be paired with a `[name]ScreenContent` composable, which accepts the state and callbacks from the ViewModel as parameters. This composable must be marked `internal` so that it can be used in snapshot tests to verify the visual appearance of the screen without requiring a ViewModel

#### **4.2.1.1 Dialog / Overlay Screens**
* **Definition**: A Screen that is presented as a dialog or overlay on top of the current screen, rather than pushing to the navigation backstack
* **Rule [R-UI-12]** `📋 guidance`: Dialog/overlay screens must use the `navigationDestination` DSL (a property-based screen) with `metadata = { directOverlay() }` to declare themselves as an overlay
* **Rule [R-UI-13]** `🔶 construct`: Dialog/overlay screens must still be annotated with `@NavigationDestination`. The property name should end in either `Screen` (e.g. `changeRoleScreen`) or `Destination` (e.g. `noCampaignsDestination`) — both are accepted because the property *is* the destination declaration site. Composable function screens (the standard backstack-push pattern) must always end in `Screen`.
* **Rule [R-UI-14]** `📋 guidance`: Dialog/overlay screens that need a ViewModel should call `viewModel()` inside the `navigationDestination` block
* **Note**: Regular screens that push to the backstack should use the standard `@Composable fun` pattern. The property-based `navigationDestination` DSL is specifically for screens that need to declare custom metadata (such as `directOverlay()`)
* **Example**:
```kotlin
// Destination (in :api)
@Serializable
data class ChangeRoleDestination(
    val memberName: String,
    val currentRole: CampaignRole,
) : NavigationKey.WithResult<CampaignRole>

// Screen (in :client) — property-based with directOverlay metadata
@NavigationDestination(ChangeRoleDestination::class)
val changeRoleScreen = navigationDestination<ChangeRoleDestination>(
    metadata = { directOverlay() }
) {
    val viewModel: ChangeRoleViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    ChangeRoleDialog(
        memberName = state.memberName,
        selectedRole = state.selectedRole,
        onRoleSelected = viewModel::onRoleSelected,
        onConfirm = viewModel::onConfirm,
        onDismiss = viewModel::onDismiss,
    )
}
```

#### **4.2.2 Destinations (NavigationKeys)**
* **Definition**: A serializable data class or object representing the navigation contract for a particular screen; the input parameters required by that screen (if any) and the output result type provided by that screen (if any)
* **Rule [R-UI-15]** `🔶 construct`: Destinations must be named `[name]Destination`
* **Rule [R-UI-16]** `🔶 construct`: Destinations must implement either `dev.enro.NavigationKey` or `dev.enro.NavigationKey.WithResult<T>`, depending on whether or not the Destination returns a result
* **Rule [R-UI-17]** `📋 guidance`: Destinations should accept the minimal amount of data required to initialise the associated Screen
    * **Example**: A Destination should accept a `User.Id`, and then the Screen should use this to load the associated `User` object, rather than the Destination accepting an entire `User` object
* **Rule [R-UI-18]** `🔶 construct`: Destinations must be serializable and annotated with `@Serializable`
* **Rule [R-UI-19]** `📋 guidance`: Destinations may be defined in the `:api` module if they should be exposed to other features as an entry point to the feature (or if they are used by the `:server` module for server driven navigation), or they may be defined in the `:client` module if they are only used internally within the feature

#### **4.2.3 ViewModels**
* **Definition**: A class that manages the UI state for a Screen and orchestrates calls to domain interfaces to load data and perform side effects based on user actions
* **Rule [R-UI-20]** `🔶 construct`: ViewModels must be named `[Name]ViewModel`
* **Rule [R-UI-21]** `🔶 construct`: ViewModels must be defined in their own file (e.g. `../[Name]ViewModel.kt`)
* **Rule [R-UI-22]** `🔶 construct`: ViewModels must have a 1:1 relationship with a [ViewModel State](#424-viewmodel-state) type
* **Rule [R-UI-23]** `🔶 construct`: ViewModels must have a single immutable public property `val state: dev.isaacudy.udytils.state.ViewModelState<T>`, where `T` is the `[Name]State` type associated with the ViewModel
* **Rule [R-UI-24]** `🔶 construct`: ViewModels must have a `private val navigation: dev.enro.NavigationHandle<T>` where `T` is the `[Name]Destination` associated with the ViewModel; this `NavigationHandle<T>` is used to read the Destination parameters and perform navigation (opening other Destinations or closing/completing the current Destination)
    * **Note**: When closing/completing a screen, use `NavigationHandle.close` when the user is cancelling or backing out, and `NavigationHandle.complete` when the user has successfully performed an action
* **Rule [R-UI-25]** `🔶 construct`: `public` or `internal` functions on a ViewModel must only return `Unit` (or omit a return type)
* **Rule [R-UI-26]** `📋 guidance`: ViewModels should inject domain interfaces to load and manipulate domain objects
* **Rule [R-UI-27]** `✅ tested`: ViewModels must be injected into `@Composable` screens using `viewModel()`, not `koinViewModel()`
* **Rule [R-UI-28]** `✅ tested`: ViewModels must use `dev.isaacudy.udytils.coroutines.JobManager` to manage Jobs/Coroutines that need management — do not maintain `var job: Job?` references

#### **4.2.4 ViewModel State**
* **Definition**: The complete, immutable representation of a Screen's data at a single point in time.
* **Rule [R-UI-29]** `📋 guidance`: ViewModel State objects must have a 1:1 relationship with a [ViewModel](#423-viewmodels) type
* **Rule [R-UI-30]** `🔶 construct`: ViewModel State objects must be `data class`
* **Rule [R-UI-31]** `🔶 construct`: ViewModel State objects must be defined in their own file (e.g. `../[Name]State.kt`)
* **Rule [R-UI-32]** `🔶 construct`: ViewModel State objects must be immutable (val properties only)
* **Rule [R-UI-33]** `✅ tested`: ViewModel State objects must use `dev.isaacudy.udytils.state.AsyncState<T>` and `dev.isaacudy.udytils.state.UpdatableState<T>` to represent data that is loaded asynchronously or the progress of asynchronous actions (e.g. the state of a "save" action might be represented as `AsyncState<Unit>`)
    * **Note**: Never directly construct `AsyncState.Loading`, `AsyncState.Success`, or `AsyncState.Error`. Use `AsyncState.fromSuspending` or `AsyncState.fromFlow` instead.
* **Rule [R-UI-34]** `📋 guidance`: ViewModel State objects must not define custom sealed classes or sealed interfaces for loading/success/error states — use `AsyncState<T>` instead. Custom sealed types for async status duplicate `AsyncState` semantics and bypass its built-in exception handling.
* **Rule [R-UI-35]** `📋 guidance`: ViewModel State objects should be a "transparent container" for domain objects and must not map domain data into lossy UI-level concepts (e.g. mapping a User into a UserListItem)
* **Rule [R-UI-36]** `📋 guidance`: ViewModel State objects should include `init` blocks that enforce invariants (rules that must be true for the object to be considered "valid")
* **Rule [R-UI-37]** `📋 guidance`: Formatting and visual representation (e.g., string concatenation, date formatting, or resource resolution) must be handled by the [Screen](#421-screens) or specialized `@Composable` properties/functions
    * **Note**: Both regular properties/functions and extension properties/functions are a valid choice here, depending on the situation
* **Example**:
    ```kotlin
    // feature.user.ui.UserDetailState.kt
    data class UserDetailState(
        val user: User,
        val isEditing: Boolean,
    ) {
        // Calculated property for logic
        val canEditName: Boolean get() = user.isVerified && isEditing
    }
        
    // feature.user.ui.UserDetailScreen.kt
    // Extension property for display
    val User.displayRole: String
        @Composable
        get() = when(role) {
            User.Role.Admin -> stringResource(Res.string.role_admin)
            User.Role.Member -> stringResource(Res.string.role_member)
        }
    ```

#### **4.2.5 UI Composables (non-screen)**
* **Definition**: A `@Composable` function defined in the `..ui..` package that is **not** a [Screen](#421-screens) — typically a sub-component used by one or more screens, an inline editor, or a feature-specific overlay
* **Rule** `🔶 construct`: Must be annotated with `@Composable`
* **Rule** `🔶 construct`: Must not be a Screen (no `@NavigationDestination`, doesn't end in `Screen`)
* **Note**: For reusable design-system primitives (buttons, fields, marks), prefer a Parchment composable in `:platform:client:ui`. Feature-local composables live alongside the Screen they support, and may be `internal` so snapshot tests can drive them.

#### **4.2.6 UI value types**
* **Definition**: A small closed value type (enum, sealed class, or sealed interface) that lives in `..ui..` and crosses feature boundaries — e.g. a `Slot` tag that one feature's ViewModel passes back to another feature's screen
* **Rule** `🔶 construct`: Must be an `enum class`, `sealed class`, or `sealed interface`
* **Rule** `🔶 construct`: Must contain no member functions; pure data shape only
* **Note**: If a value type grows behaviour, it stops being a value type — promote it into a State, Destination, or domain object as appropriate.

### **4.3 `data` package constructs (`:client` only)**

The `data` axis is **client-only**. Server-side persistence and service implementations live in the `services` axis (see 4.4). The client's `data` package holds Repositories that fan out across [Services](#441-services) (the `:api` contract) and client-side local storage.

#### **4.3.1 Repositories**
* **Definition**: A class that provides implementations for [Domain Interfaces](#411-domain-interfaces), providing the "edge" of the domain layer
* **Rule [R-DATA-04]** `🔶 construct`: Repositories must be named `[Name]Repository`
* **Rule [R-DATA-05]** `🔶 construct`: Repositories must be marked as `internal`
* **Rule [R-DATA-06]** `🔶 construct`: Repositories must not implement [Domain Interfaces](#411-domain-interfaces) directly
* **Rule [R-DATA-07]** `🔶 construct`: Repositories must expose [Domain Interfaces](#411-domain-interfaces) as `public val` properties
* **Note**: The property name must match the interface name using `lowerCamelCase` (e.g., `val createUser = CreateUser { ... }`)
* **Note**: Properties must be initialized immediately; they must not be `lazy` or use custom getters
* **Rule [R-DATA-08]** `🔶 construct`: Repositories are forbidden from injecting [Domain Interfaces](#411-domain-interfaces) (the same constraint as R-DATA-02, restated here at the construct level)
* **Rule [R-DATA-09]** `🔶 construct`: Repositories are forbidden from injecting other Repositories
* **Rule [R-DATA-10]** `📋 guidance`: Repositories may inject [Services](#441-services), client-side `data.storage` Storage objects, or database clients to fulfill their domain properties.
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

#### **4.3.1.1 Non-Repository client data abstractions**
* **Definition**: A client-side interface or class declared in `..data..` that is **not** a Repository — typically a low-level concern with platform-specific actuals (e.g., `BinaryUploadClient` for chunked file upload)
* **Rule** `🔶 construct`: Must live in `feature.[name].data` (not `data.storage`)
* **Rule** `🔶 construct`: Must not be named `Repository`
* **Note**: These exist to give Repositories a clean abstraction over a concrete platform capability. If you find yourself writing one, ask whether it belongs in `:platform:client` instead — feature-local data abstractions are appropriate when the contract is feature-specific.

#### **4.3.2 `data.storage` package constructs (`:client` only)**

##### **4.3.2.1 Client-side Storage classes**
* **Definition**: A class responsible for local-device data persistence and retrieval (e.g., credentials, preferences, cached data on disk)
* **Rule [R-DATA-11]** `🔶 construct`: Storage classes must be named `[Name]Storage`
* **Rule [R-DATA-12]** `🔶 construct`: Storage classes must not be `data class`
* **Rule [R-DATA-13]** `🔶 construct`: Storage classes must reside in the `data.storage` package on `:client`
* **Rule [R-DATA-14]** `📋 guidance`: Storage classes must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)
* **Rule [R-DATA-15]** `✅ tested`: Storage classes are forbidden from injecting [Domain Interfaces](#411-domain-interfaces), [Repositories](#431-repositories), or [Services](#441-services)
* **Note**: Client-side Storage classes may be `expect`/`actual` classes when the underlying storage mechanism is platform-specific (e.g., Keychain on iOS, SharedPreferences on Android)
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

### **4.4 `services` package constructs**

The `services` axis covers both the `:api` Service contract and the entire `:server` implementation surface — ServiceImpls, internal helpers/orchestrators, and Postgres tables.

#### **4.4.1 Services (the cross-the-wire contract)**
* **Definition**: The client-server contract (in `:api`) and its implementation (in `:server`). Services use **urpc** (`dev.isaacudy.udytils:urpc-*`): KSP generates the client, the `UrpcService` server binding, and the wire descriptors from the annotated interface.
* **Rule [R-SVC-03]** `📋 guidance`: When creating new functionality, always implement services as urpc service functions in the appropriate server module — do not build client-only local services.
* **Rule (`:api`)**: A service is an `interface` annotated `@Urpc` whose functions are plain: `suspend fun f(req): Res` (unary), `fun f(req): Flow<Res>` (server-streaming), or `fun f(reqs: Flow<Req>): Flow<Res>` (bidirectional). Each function takes 0 or 1 parameter; use a zero-arg function when no input is needed.
* **Rule (`:api`)**: A function's `Request`/`Response` types are declared with the service as nested `@Serializable` types, grouped under an `object` namespace per function (e.g. `object CreateUser { @Serializable data class Request(...); @Serializable data class Response(...) }`). Omit the parameter for no input, and use no return type (Unit) for no output.
* **Rule (`:api`)**: Service interfaces live in `feature.[name].services` of the `:api` module.
* **Rule (`:api`)**: Service functions must expect errors to be propagated using thrown exceptions; a service return type should only ever represent a successful result.
    * **Note**: Known exceptions should be defined as their own type, either in the domain package (if they're expected to be visible to domain interfaces or the UI) or within the `services` package (if they're expected to be handled at the service-call layer). Make them `@Serializable` (and ideally `PresentableException` with a deliberate `retryable` flag — see [§5.1](#51-exception-handling)).
* **Example**:
```kotlin
// feature.user.services.UserService.kt (:api)
@Urpc
interface UserService {
    suspend fun createUser(request: CreateUser.Request): CreateUser.Response
    suspend fun getUser(request: GetUser.Request): GetUser.Response
    fun observeUsers(): Flow<ObserveUsers.Response>

    object CreateUser {
        @Serializable data class Request(val name: String, val email: String)
        @Serializable data class Response(val user: User)
    }
    // ...
}
```

#### **4.4.2 Service implementations (`:server`)**
* **Definition**: Implementations of `Service` interfaces (see 4.4.1).
* **Rule [R-SVC-04]** `🔶 construct`: For a service named `[name]Service` the service implementation should be suffixed with `Impl` (e.g. `[name]ServiceImpl`)
* **Rule [R-SVC-05]** `🔶 construct`: Service implementations must be `internal`
* **Rule [R-SVC-06]** `🔶 construct`: Service implementations must reside in `feature.[name].services` of the `:server` module (alongside the interface they implement, dual-life in the same package name).
* **Rule [R-SVC-07]** `🔶 construct`: Service implementations are forbidden from injecting [Domain Interfaces](#411-domain-interfaces)
* **Rule [R-SVC-08]** `📋 guidance`: Service implementations may inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api`.

#### **4.4.3 `services.internal` package**

* **Definition**: Server-side coordinator and helper classes — the things that do the work the ServiceImpl orchestrates.
* **Bare `services.internal`**: top-level orchestrators (e.g. `SessionProcessingManager`) that compose multiple subsystems, **plus** shared-payload data types that flow between subsystems through the orchestrator.
* **`services.internal.<subsystem>`**: subsystem packages. Each direct child of `services.internal` is a sealed island under hierarchical visibility (see 3.4.5).
* **Rule [R-SVC-09]** `✅ tested`: A class in `services.internal.<subsystem>.**` may not import from a different subsystem under `services.internal`. Shared payload types belong at a common ancestor (typically bare `services.internal`); the rule allows ancestor data-shape imports for exactly this reason.

The package may contain the following construct kinds:

* **Coordinator classes** — concrete (non-`abstract`, non-`data`) classes that compose subsystems. The orchestrator at bare `services.internal` is the canonical example.
* **Data carriers** — `data class` payloads that flow between subsystems through the orchestrator. These usually live at the bare `services.internal` ancestor so both producer and consumer subsystems can name them under the data-shape carve-out.
* **Internal interfaces** — abstractions used inside a subsystem (e.g., a strategy contract whose implementations live in the same subpackage).
* **Object helpers** — `object` declarations holding pure helpers.
* **Internal exceptions** — exception types thrown only by internal helpers; service-level exceptions belong on the `Service` interface (see [4.4.1](#441-services-the-cross-the-wire-contract)).

#### **4.4.4 `services.storage` package — Postgres persistence**

> **ukpt status**: the Postgres toolkit lives in the `embedded-udytils` submodule (`:postgres-core/koin/codegen/gradle-plugin/embedded`), so these rules are the documented persistence standard. The `:platform:server:postgres` module that applies the codegen plugin and owns the Flyway migrations is **created when the first server feature needs persistence** — until then the `services.storage` rules below pass vacuously (no storage code exists yet).

* **Definition**: A feature's persistence storage classes and mappings, built on **[Exposed](https://github.com/JetBrains/Exposed)** and the **`dev.isaacudy.udytils.postgres`** runtime. That runtime (in the `embedded-udytils` submodule, re-exported by `:platform:server:postgres` via `api(libs.udytils.postgres.core)`) provides `PostgresConfig`, `PostgresMigrator`, `PgNotificationBus`, and the custom Exposed column types (`JsonbColumnType`, `JsonColumnType`, `TextArrayColumnType`, `TimestampColumnType`) — do **not** hand-roll these in feature code; extend the library instead.
* **Contents (hand-written, in the feature)**: `[Name]Storage` classes, mapping functions (conventionally collected in `[Name]Mappers.kt`), and codec objects.
* **Contents (generated, NOT in the feature)**: the Exposed `Table` objects and `XxxRow` data classes are generated into the **shared `platform.server.postgres.tables` package** (`:platform:server:postgres`) and imported across every feature's storage code — see [§4.4.4.2](#4442-table-objects-generated) and the pipeline in [§4.4.4.7](#4447-postgres-codegen-pipeline--runtime).

##### **4.4.4.1 Storage classes**
* **Rule [R-SVC-10]** `🔶 construct`: Named `[Name]Storage` (or `[Name]Store` where the broader name fits)
* **Rule [R-SVC-11]** `🔶 construct`: Not abstract, not data class
* **Rule [R-SVC-12]** `🔶 construct`: `internal` visibility
* **Rule [R-SVC-13]** `✅ tested`: Must take/return `XxxRow` types only — never domain types. Domain conversion lives in mapping functions (see 4.4.4.4).

##### **4.4.4.2 `Table` objects (generated)**

> All `Table`/`Row` rules in §4.4.4.2–§4.4.4.3 are `⚙️ codegen` — guaranteed by the `dev.isaacudy.udytils.postgres` plugin, not by Konsist (the generated sources live under `build/generated/` and are never scanned). They live in the shared `platform.server.postgres.tables` package, not in any feature's `services.storage`.

* **Rule [R-SVC-14]** `⚙️ codegen`: `Table`/`Row` sources are generated by the **`dev.isaacudy.udytils.postgres`** Gradle plugin (applied in `:platform:server:postgres`) from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables`. The plugin registers two tasks — `generatePostgresTables` (the Exposed sources) and `exportPostgresSchema` (the committed `schema.sql` snapshot). Generated files live under `build/generated/source/postgres-tables/`, carry a `Generated by the dev.isaacudy.udytils.postgres Gradle plugin` header, and are not committed.
* **Rule [R-SVC-15]** `⚙️ codegen`: Each persisted entity has a generated `object XxxTable : Table("xxx")` (plural, matching the SQL table name); custom columns are typed with the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …).
* **Rule [R-SVC-16]** `⚙️ codegen`: Every column on the SQL table is declared on the `Table` object, with no omissions. The generated UUID primary key is emitted as `uuid("id").autoGenerate()`, but the write path always supplies the id explicitly via `Domain.toRow(...)` / `setFromRow`, so the generated default is never relied upon.

##### **4.4.4.3 `Row` data classes (generated)**
* **Rule [R-SVC-17]** `⚙️ codegen`: The in-memory persistence shape is a top-level `data class XxxRow` (singular). Fields use only **primitive types** — no domain wrappers, no enums, no sealed hierarchies.
* **Rule [R-SVC-18]** `⚙️ codegen`: Each generated file exposes a "fake-constructor" `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes.
* **Example**:
```kotlin
// Read
val row: UserProfileRow? = UserProfilesTable
    .selectAll()
    .where { UserProfilesTable.userId eq userId }
    .singleOrNull()
    ?.let(::UserProfileRow)

// Write
UserProfilesTable.upsert(UserProfilesTable.userId) {
    it.setFromRow(row)
}
```

##### **4.4.4.4 Mapping functions**
* **Rule [R-SVC-19]** `📋 guidance`: Conversions between a generated `XxxRow` and a domain type live in `services.storage` as plain `internal fun` declarations, conventionally collected in `[Name]Mappers.kt`.
* **Convention**: `XxxRow.toDomain()` for `Row → Domain`; `Domain.toRow(...)` for the inverse.
* **Rule [R-SVC-20]** `📋 guidance`: Where storage operations span multiple tables to assemble a richer "record" type, define those higher-level helpers as `suspend fun [Name]Storage.loadXxx(...)` extensions in `services.storage`.

##### **4.4.4.5 Partial updates**
* **Rule [R-SVC-21]** `📋 guidance`: When an operation touches only a subset of columns, keep the hand-written `update { ... it[col] = value ... }` block. `setFromRow` writes every column and is wrong for these cases.

##### **4.4.4.6 Codec objects**
* **Definition**: The read/write codec for a column whose on-disk shape differs from the domain shape — either an `object` holding discriminator constants (e.g. `ChatMessageContentTypeCodec`, `ProcessingStatusCodec`) or file-private `Json` + `encode`/`decode` helpers in the `[Name]Mappers.kt` file (e.g. the JSONB relationship/metadata codecs in `EntityMappers`). Both forms are acceptable.
* **Rule** `🔶 construct`: Lives in `services.storage` alongside the Row + mapping functions for the table that uses it
* **Note**: Codecs encapsulate the read/write asymmetry that `setFromRow` can't express. Keep them small and keyed to the column they serve.

##### **4.4.4.7 Postgres codegen pipeline & runtime**

The persistence stack is built on the **`dev.isaacudy.udytils.postgres`** library (developed in the `embedded-udytils` submodule) plus **Exposed**, **Flyway**, and a **Zonky** embedded Postgres:

* **Schema** lives only in `:platform:server:postgres/src/main/resources/db/migration/` as Flyway scripts — versioned `V<n>__snake_name.sql` (run once, in order) and repeatable `R__name.sql` (re-run whenever their checksum changes, e.g. `R__notify_triggers.sql`). A schema change is a **new** `V<n>` file; existing `V<n>` files are never edited in place.
* **`exportPostgresSchema`** Flyway-migrates a throwaway Zonky Postgres and writes a normalised `schema.sql` snapshot; **`generatePostgresTables`** then emits the Exposed `Table`/`Row` sources from it into `platform.server.postgres.tables`. Both tasks are registered by the `dev.isaacudy.udytils.postgres` Gradle plugin and run before `compileKotlin`.
* **Runtime ownership**: the DB primitives (`PostgresConfig`, `PostgresMigrator`, `PgNotificationBus`, the column types) live in the udytils library; `:platform:server:postgres` owns only the SQL migrations + codegen wiring and re-exports the runtime; the **application** (`:app:server`) owns its connection config (`ukptPostgresConfigFromEnv()`), wires `postgresDependencies(config)` (from `dev.isaacudy.udytils.postgres.koin`), and runs `PostgresMigrator.migrate()` before it starts serving.

##### **4.4.4.8 Reactive storage flows (`PgNotificationBus`)**

A `[Name]Storage` class may expose `Flow` reads that re-query when a Postgres `NOTIFY` fires, by injecting `dev.isaacudy.udytils.postgres.PgNotificationBus`:

* **Rule** `📋 guidance`: The channel name is a `companion object const val CHANNEL` and **must** match a `pg_notify(...)` trigger in the migrations (e.g. `R__notify_triggers.sql`). The shape is: emit an initial query, then `bus.listen(CHANNEL).filter { it == key }.collect { emit(query()) }`. This is the canonical reactive-read shape across the storage classes (campaigns, chat, entities, events, sessions, user); it is convention, not statically enforced.

#### **4.4.5 `services.tools` package (reserved)**
* **Definition**: Reserved for AI tool-use subclasses (e.g. `AssistantTool` wrappers around a service). ukpt has no AI subsystem, so `services.tools` is intentionally **empty** — its layer defines no constructs, so any declaration placed here fails the layer-membership meta-test until a construct is defined for it.
* **Rule [R-SVC-25]** `✅ tested`: Anything placed in `services.tools` must not depend on `services.storage` or `services.internal` — tools may only depend on the `:api`-defined Service contract. The isolation rule is enforced now even though the package is empty.
* **Note**: If an AI subsystem is added later, reintroduce an `isAssistantTool` construct (extends `AssistantTool`, named `[Action][Entity]Tool`) in `ServicesLayer.Tools` to populate this layer — see the upstream reglyph project for the full shape.

### **4.5 top-level package constructs**

The top-level `feature.[name]` package is reserved for DI wiring. Concrete classes (ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.

#### **4.5.1 Dependency modules**
* **Definition**: The configuration for Dependency Injection (DI) that wires the feature together
* **Rule [R-FEAT-01]** `🔶 construct`: DI modules must be defined in the top-level `feature.[name]` package of the `:client` and `:server` modules
* **Rule [R-FEAT-02]** `🔶 construct`: DI modules are Koin `val` modules whose names end in `Dependencies`. The convention is `[name]ClientDependencies` in `:client` and `[name]ServerDependencies` in `:server` (e.g. `val entityServerDependencies = module { ... }`). The construct test enforces the `Dependencies` suffix; the `Client`/`Server` infix is convention.
* **Rule [R-FEAT-03]** `✅ tested`: The DI module for a feature must only bind/provide dependencies that are both defined and implemented in that feature
    * **Example**: It is forbidden for "featureA" to implement and bind a domain interface that is defined by "featureB"
* **Rule [R-FEAT-04]** `✅ tested`: DI bindings must use the constructor reference style: `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`
* **Rule [R-FEAT-05]** `📋 guidance` (server, `@Urpc` services): Register a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block:
  ```kotlin
  scope<UrpcCall> {
      scopedOf(::UserProfileServiceImpl)
          .bind(UserProfileService::class)
          .bindService(::UserProfileServiceUrpcBinding)
  }
  ```
    * **Why**: `bindService` (from `dev.isaacudy.udytils.urpc.koin`) registers the binding under its own concrete type, bound to `UrpcService`, with the impl resolved lazily. Do **not** use `scoped<UrpcService> { [Name]ServiceUrpcBinding { get() } }` — every such binding shares the same definition key (`UrpcService`), so when more than one service is registered in a scope they override each other and the host's `getAll<UrpcService>()` returns only one, 404-ing the rest. (`urpcService(::[Name]ServiceUrpcBinding)` is the equivalent standalone form when there is no impl definition to chain off.)
* **Note**: It is the responsibility of `:app` level modules (application shells) to collect all of the DI modules provided by feature modules and create the final dependency graph
* **Note**: When a new dependency module is added, it must be registered in both `:app:client:shared` and `:app:server`
* **Note**: When a new Service is added, it must be registered in `:app:server`
* **Example**:
```kotlin
// feature.user.UserServiceImpl.kt (:server)
internal class UserServiceImpl(
    private val userStorage: UserStorage,
    private val sessionAuth: SessionAuth,
) : UserService {

    override suspend fun createUser(request: UserService.CreateUser.Request): UserService.CreateUser.Response {
        val userId = sessionAuth.requireUser().first()
        val user = userStorage.insertUser(
            row = Users.Row(name = request.name, email = request.email)
        )
        return UserService.CreateUser.Response(user = user)
    }

    override suspend fun getUser(request: UserService.GetUser.Request): UserService.GetUser.Response {
        val user = userStorage.getUser(request.userId)
        return UserService.GetUser.Response(user = user)
    }

    override fun observeUsers(): Flow<UserService.ObserveUsers.Response> =
        userStorage.observeAll()
            .map { UserService.ObserveUsers.Response(users = it) }
}
```

---

## **5. Project-Wide Code Rules**

### **5.1 Exception handling**
* **Rule [R-PROJ-01]** `✅ tested`: `try/catch` blocks must never catch `Exception`. Use `catch (t: Throwable)` or catch a specific exception type instead.
    * **Why**: The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserializes server-side exceptions into types that may not extend `Exception` (e.g. kotlinx-serialization / kRPC error types). A `catch (Exception)` block will silently miss these, causing errors to propagate uncaught and crash on internal threads instead of being handled by application code.
    * **Note**: On the client side, prefer `AsyncState.fromSuspending` over manual `try/catch` — it handles exceptions correctly and integrates with the ViewModel state pattern.
    * **Note**: Catching a specific exception type (e.g. `catch (t: IllegalArgumentException)`) is always acceptable when you only want to handle that specific case.
* **Rule [R-PROJ-02]** `✅ tested`: Exception types defined in `services` (e.g. `CampaignService.UserNotFoundException`) must be annotated with `@Serializable` so the urpc transport can carry them to the client with the correct type and message. Prefer subclassing `PresentableException` with a deliberate `retryable` flag — streaming flows auto-retry retryable errors and surface terminal ones, and the unary error UI offers a Retry action only when `retryable`.

### **5.2 Imports**
* **Rule [R-PROJ-07]** `✅ tested`: Imports must not use wildcards. Always list the explicit symbols.
    * **Why**: Wildcards hide which symbols a file depends on, break a number of architecture-test checks (which inspect import names directly), and silently pull in new names when the imported package adds members.

---

## **6. Architecture Exceptions**

Architecture rules are enforced by Konsist-based tests in `:platform:common:architecture`. When a specific declaration cannot conform to a rule (e.g. a transitional class whose ideal location hasn't been determined yet), the declaration can be marked exempt from that rule so the tests pass while the exception is tracked explicitly.

### **6.1 How to add an exception**

There are two exemption mechanisms, depending on what kind of file the exempt code lives in:

#### **Kotlin source files: `@ArchitectureException`**

Add the [`@ArchitectureException`](src/main/kotlin/architecture/ArchitectureException.kt) annotation either at file level (above the `package` line) or on the specific declaration:

```kotlin
@file:ArchitectureException(
    ruleIds = ["R-MOD-04", "R-MOD-06"],
    reason = "Sessions reaches into Entities for transcription phrase hints. The shared " +
        "EntityStorage accessor hasn't been promoted to :platform yet — until it is, the " +
        "cross-feature import is the cheapest way to keep a single authoritative Postgres " +
        "access layer.",
    trackingIssue = "",
)

package feature.sessions.services.internal.audio

import architecture.ArchitectureException
import feature.entities.services.storage.EntityStorage
// ...
```

`ruleIds` lists the rules the declaration is exempt from (using the IDs in this README — see the [Rule IDs](#rule-ids) section). `reason` is free-form prose; `trackingIssue` is optional but recommended.

The architecture tests look up the annotation via Konsist when checking each rule, and skip declarations / files that list the rule's ID.

#### **Gradle build files: `// architecture-exception:` comment**

`build.gradle.kts` files can't carry the annotation (no compile classpath). Place a comment immediately above the dependency line:

```kotlin
sourceSets {
    commonMain.dependencies {
        // architecture-exception: R-MOD-10
        // reason="Pulls feature-level analytics types that haven't yet been promoted to " +
        //   ":platform:common:analytics. Refactor tracked separately."
        implementation(projects.feature.core.api)
    }
}
```

The exemption applies to the immediately-following dependency line. Multiple `architecture-exception:` lines may stack to exempt one declaration from several rules (`// architecture-exception: R-MOD-10, R-MOD-09`).

### **6.3 Rules for adding exceptions**

* **Rule [R-PROJ-03]** `📋 guidance`: Architecture exceptions must only be added after discussing the exception with a human author. Adding an exception is an acknowledgement that the code does not currently conform to the architecture, and requires human judgement to determine whether the exception is acceptable.
* **Rule [R-PROJ-04]** `📋 guidance`: Adding an architecture exception is **not** a valid way to resolve an immediate architecture test failure without user feedback. If a test fails, the correct first step is to fix the code or update the architectural rules — not to suppress the failure with an exception.
* **Rule [R-PROJ-05]** `📋 guidance`: Every exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and what the intended resolution is.
* **Rule [R-PROJ-06]** `📋 guidance`: Exceptions should be treated as temporary. They should be revisited periodically and removed once the underlying issue is resolved.
