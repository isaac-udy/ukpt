# UKPT Architecture
This document describes the architecture that should be used for the UKPT project. 

## **1. Gradle Project Structure**

The project is organized into three root-level module groups.

### **1.1 `:app` (Application shells)**

* **Purpose**: Final executable entry points and dependency injection (DI) wiring.
* **Structure**: May contain sub-groups (e.g., `:app:admin`, `:app:customer`) if multiple applications are built from the same codebase.
* **Child Modules**: Each app contains a `:client` (Mobile/Desktop/Web) and/or a `:server` (Ktor executable).
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
    * **`:server`**: Server-only infrastructure (e.g., SQL connection pools, Ktor plugins).

---

## **2. Gradle Project Dependency Rules**

### **2.1 Feature Constraints**

* **Rule**: `:feature` modules must never depend on `:app` modules.
* **Rule**: `:feature` modules may depend on `:platform` modules.
* **Rule**: `:feature:[name]:client` modules must never depend on any other `:client` or `:server` module
* **Rule**: `:feature:[name]:client` modules may depend on any `:feature:[name]:api` module
* **Rule**: `:feature:[name]:server` modules must never depend on any other `:client` or `:server` module
* **Rule**: `:feature:[name]:server` modules may depend on any `:feature:[name]:api` module
* **Rule**: `:feature:[name]:api` modules may depend on another `:feature:[name]:api` module to share models or interfaces.
    * **Note**: `:api` to `:api` dependencies are allowed, but should be used sparingly, treated with caution, and minimised where possible.
* **Rule**: `:feature` modules may be grouped (e.g. `:feature:[group]:[name]:api/client/server`)
    * **Note**: A module that serves as a group should exist only as a group, and should not itself contain `:api`, `:server` or `:client` modules.

### **2.2 Platform Constraints**
* **Rule**: `:platform` modules must never depend on `:app` modules.
* **Rule**: `:platform` modules must never depend on `:feature` modules.
* **Rule**: `:platform` modules may depend on other `:platform` modules.
    * **Note**: `:platform` to `:platform` dependencies are allowed, but should be used sparingly, treated with caution, and minimised where possible.

---

## **3. Feature Architecture (Package level)**

Every feature module follows a strict package hierarchy: `feature.[name].[package]`.

The top-level package `feature.[name]` is also used for dependency injection wiring.

All sub-packages may include subpackages for grouping. For example, a `..ui` package in a feature that includes both list and detail functionality may have `feature.[name].ui.list` and `feature.[name].ui.detail`. This same pattern applies for the domain and data packages.

### **3.1 `domain` package (in `:api`, `:client`, and `:server`)**

* **Contents**: Pure Kotlin Data Models and single-function interfaces (Interactors).
* **Rule**: Must not contain any platform-specific dependencies (e.g., Android, Ktor, SQL).
* **Rule**: Must not depend on `ui` or `data` packages within the feature.
* **Rule**: May depend on another feature's `domain` package, but only if the dependency is on code defined in the `:api` module of the other feature. This is enforced by the general cross-feature dependency rule (see 1.2).
    * **Note**: Cross-feature domain dependencies should be minimised where possible, but are permitted because real-world domains have genuine dependencies between them. The important thing is getting the direction of dependencies correct and avoiding circular dependencies.

### **3.2 `ui` package (in `:api` and `:client`)**

* **`:api` Contents**: Serializable Navigation Keys.
* **`:client` Contents**: Compose UI, ViewModels, and UI-state models.
* **Rule**: May depend on `domain`.
* **Rule**: Forbidden from *implementing* `domain` interfaces.
* **Rule**: Forbidden from depending on `data` or `data.services`.

### **3.3 `data` package (in `:client` and `:server`)**

* **Contents**: Repository implementations and persistence logic.
* **Rule**: Implements `domain` interfaces.
* **Rule**: Forbidden from *injecting* `domain` interfaces. Logic requiring multiple domain interfaces must be moved to a UseCase in the `domain` package.

#### **3.3.1 `data.services` package (in `:api` and `:server`)**

* **`:api` Contents**: kRPC interfaces annotated with `@Rpc`.
* **`:server` Contents**: `AssistantConfig` subclasses that configure AI model invocations made by service implementations.
* **Rule**: Acts as the entry point for the server, mirroring the `ui` package's role on the client.
* **Rule**: Forbidden from *injecting* `domain` interfaces (same as the parent `data` package).
* **Note**: Concrete implementations of `@Rpc` interfaces must reside in the top-level feature package (see [4.4.2](#442-service-implementations-server-only)), not in `data.services`.

#### **3.3.1.1 `data.services.tools` package (in `:server`)**

* **Contents**: `AssistantTool` subclasses that expose a feature's service as an AI tool.
* **Rule**: Tool classes must reside in the `data.services.tools` subpackage of the feature whose service they primarily use.
* **Rule**: Must not depend on `data.storage`.

#### **3.3.2 `data.storage` package (in `:api`, `:client`, and `:server`)**

* **`:api` Contents**: Document classes (see [4.3.2.2](#4322-entity--document-classes)) that define collection/serialization for domain objects shared between client and server.
* **`:client` Contents**: Document classes, Entity classes and Storage classes for client-side persistence.
* **`:server` Contents**: Document classes, Entity classes, Storage classes, and conversion extensions for server-side persistence.

### **3.4 top-level package (in `:client` and `:server`)**
* **Contents**: Dependency injection modules which define dependency injection bindings

---

## **4. Feature Architecture (Code level)**

Within the packages of a feature module, every class, function or other code-level construct is defined as a component in the architecture, based on it's responsibilities and package location.

### **4.1 `domain` package constructs**

The `domain` package must only contain [domain interfaces](#411-domain-interfaces), [UseCases](#412-usecases), [domain objects](#413-domain-objects), [domain extension functions](#414-domain-extension-functions), and exceptions. Every class must fall into one of these categories, and every interface must be either a `fun interface` (domain interface) or a `sealed interface` (domain object).

#### **4.1.1 domain interfaces**
* **Definition**: A functional interface representing domain-level functionality/business logic
* **Rule**: Domain interfaces must be a `fun interface`
* **Rule**: The primary function of a domain interface must be an `operator fun invoke`
* **Rule**: Domain interfaces may define additional default functions that call the primary function
    * **Note**: Default functions in a domain interface do not need to be `operator fun invoke` functions, and should aim to use expressive names
    * **Note**: Default functions in a domain interface should aim to provide commonly used functionality (e.g. handling of a particular exception type), or to simplify calling the domain interface's primary function with particular parameters
    * **Note**: Implementations of a domain interface must never override the default functions belonging to a domain interface
    * **Note**: Convenience functions for domain interfaces must be defined as default member functions, not as top-level extension functions, so that they are discoverable and co-located with the interface definition
* **Rule**: All functions in a domain interface must either be `suspend` functions or return a `Flow<T>`
    * **Note**: Domain interfaces that return a `Flow<T>` in their primary function should be prefixed with `FlowOf`
    * **Note**: Domain interfaces functions that return a `Flow<T>` may also return the `StateFlow<T>` subtype of `Flow<T>`
* **Rule**: The parameters of the primary function of a domain interface must be [domain objects](#413-domain-objects), nested types belonging to the domain interface, primitive types, or collections of the preceding
* **Rule**: The primary function of a domain interface must return [domain objects](#413-domain-objects), nested types belonging to the domain interface, primitive types, collections of the preceding or no value
* **Rule**: Domain interface functions must expect errors to be propagated using thrown exceptions; a domain interfaces return type should only ever represent a successful result.
    * **Note**: Known exceptions should be defined as their own type that extends `RuntimeException`, either at the top-level within the domain package (in the case of exceptions that are thrown by multiple domain interfaces) or as nested class within the domain interface (in the case of exceptions that are thrown by a specific domain interface)
    * **Note**: Known exceptions should be marked on the domain interface's function using the `@Throws` annotation
    * **Note**: `@Throws` annotations on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException` (or a superclass such as `Exception`) in the exception list, as this is required for Kotlin/Native compilation
    * **Note**: Domain interface functions may also expect generic/unknown errors (e.g. RuntimeException) to occur, but these do not need to be defined as their own type or marked in `@Throws`
* **Rule**: Domain interfaces must be implemented by a [Repository](#431-repositories) (as a property of the Repository) or by a [UseCase](#412-usecases)
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

#### **4.1.3 domain objects**
* **Definition**: An immutable type representing data at the domain-level
* **Rule**: Domain objects must be immutable (val properties only)
* **Rule**: Domain objects should use nested value classes for identifiers (e.g. value class Id(val value: String)) where appropriate
* **Rule**: Domain objects should use sealed interface hierarchies to model polymorphic data where appropriate
* **Rule**: Domain objects must be annotated with `@Serializable`
* **Rule**: Domain objects should include `init` blocks that enforce invariants (rules that must be true for the object to be considered "valid")
* **Rule**: Domain objects should use nested types (enums, value classes, sealed interfaces/classes) if the nested objects are conceptually inseparable from the parent domain object, otherwise these types should be defined as their own domain objects
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

#### **4.1.2 UseCases**
* **Definition**: A class that implements a single domain interface
* **Rule**: A UseCase must implement exactly one domain interface
* **Rule**: A UseCase must not override any of the default functions provided by the domain interface
* **Rule**: A UseCase may inject domain interfaces to perform its logic
    * **Note**: If a UseCase only injects a single other domain interface to perform it's logic, consider whether it makes sense for that logic to become a default function of the other domain interface
* **Rule**: A UseCase must not contain mutable state — all properties must be `val`. Immutable helper properties (e.g., loggers) are permitted.
* **Rule**: If UseCase logic becomes too complex, consider breaking the UseCase down into smaller parts; these could be file-private extension functions, private functions within the UseCase, or nested classes within the UseCase
    * **Note**: Avoid adding additional domain interfaces/UseCases to reduce complexity in a UseCase; these domain interfaces/UseCases often end up being used only by a single UseCase, but pollute the domain namespace and add complexity to the dependency graph

#### **4.1.4 domain extension functions**
* **Definition**: A top-level extension function on a domain object that adds derived or convenience behavior
* **Rule**: The receiver type, return type, and all parameter types must be domain objects, primitive types, or collections of the preceding
* **Rule**: Domain extension functions must not introduce platform-specific dependencies
* **Note**: Prefer default member functions on domain interfaces for domain interface-related convenience logic (see [4.1.1](#411-domain-interfaces)). Extension functions are appropriate for adding behavior to domain objects (e.g., `CampaignRole.permissions()`)

### **4.2 `ui` package constructs**
#### **4.2.1 Screens**
* **Definition**: A Composable function that defines the layout and visual representation of a feature or portion of a feature
* **Rule**: Screen functions must be annotated with `@Composable`
* **Rule**: Screen functions must be bound to the Destination associated with the Screen (e.g. `[Name]Destination`) using the `@NavigationDestination` annotation
* **Rule**: Screen functions must be named `[name]Screen`
* **Rule**: Screen functions must have a single parameter, which is the ViewModel associated with the Screen (e.g. `[Name]ViewModel`)
* **Rule**: Screen functions have a 1:1 relationship with a [ViewModel](#423-viewmodels) and [ViewModel State](#424-viewmodel-state)
* **Rule**: Screen functions must observe the ViewModel's `state` property and use this to drive the state of the UI
* **Rule**: Screen functions should delegate all user interaction handling to the ViewModel
* **Rule**: Screen functions must be paired with a `[name]ScreenContent` composable, which accepts the state and callbacks from the ViewModel as parameters. This composable must be marked `internal` so that it can be used in snapshot tests to verify the visual appearance of the screen without requiring a ViewModel

#### **4.2.1.1 Dialog / Overlay Screens**
* **Definition**: A Screen that is presented as a dialog or overlay on top of the current screen, rather than pushing to the navigation backstack
* **Rule**: Dialog/overlay screens must use the `navigationDestination` DSL (a property-based screen) with `metadata = { directOverlay() }` to declare themselves as an overlay
* **Rule**: Dialog/overlay screens must still be annotated with `@NavigationDestination` and follow the naming convention `[name]Screen`
* **Rule**: Dialog/overlay screens that need a ViewModel should call `viewModel()` inside the `navigationDestination` block
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
* **Rule**: Destinations must be named `[name]Destination`
* **Rule**: Destinations must implement either `dev.enro.NavigationKey` or `dev.enro.NavigationKey.WithResult<T>`, depending on whether or not the Destination returns a result
* **Rule**: Destinations should accept the minimal amount of data required to initialise the associated Screen
    * **Example**: A Destination should accept a `User.Id`, and then the Screen should use this to load the associated `User` object, rather than the Destination accepting an entire `User` object
* **Rule**: Destinations must be serializable and annotated with `@Serializable`
* **Rule**: Destinations may be defined in the `:api` module if they should be exposed to other features as an entry point to the feature (or if they are used by the `:server` module for server driven navigation), or they may be defined in the `:client` module if they are only used internally within the feature

#### **4.2.3 ViewModels**
* **Definition**: A class that manages the UI state for a Screen and orchestrates calls to domain interfaces to load data and perform side effects based on user actions
* **Rule**: ViewModels must be named `[Name]ViewModel`
* **Rule**: ViewModels must have a 1:1 relationship with a [ViewModel State](#424-viewmodel-state) type
* **Rule**: ViewModels must have a single immutable public property `val state: dev.isaacudy.udytils.state.ViewModelState<T>`, where `T` is the `[Name]State` type associated with the ViewModel
* **Rule**: ViewModels must have a `private val navigation: dev.enro.NavigationHandle<T>` where `T` is the `[Name]Destination` associated with the ViewModel; this `NavigationHandle<T>` is used to read the Destination parameters and perform navigation (opening other Destinations or closing/completing the current Destination)
    * **Note**: When closing/completing a screen, use `NavigationHandle.close` when the user is cancelling or backing out, and `NavigationHandle.complete` when the user has successfully performed an action
* **Rule**: `public` or `internal` functions on a ViewModel must only return `Unit` (or omit a return type)
* **Rule**: ViewModels should inject domain interfaces to load and manipulate domain objects
* **Rule**: ViewModels must be injected into `@Composable` screens using `viewModel()`, not `koinViewModel()`
* **Rule**: ViewModels must use `dev.isaacudy.udytils.coroutines.JobManager` to manage Jobs/Coroutines that need management — do not maintain `var job: Job?` references

#### **4.2.4 ViewModel State**
* **Definition**: The complete, immutable representation of a Screen's data at a single point in time.
* **Rule**: ViewModel State objects must have a 1:1 relationship with a [ViewModel](#423-viewmodels) type
* **Rule**: ViewModel State objects must be `data class`
* **Rule**: ViewModel State objects must be immutable (val properties only)
* **Rule**: ViewModel State objects must use `dev.isaacudy.udytils.state.AsyncState<T>` and `dev.isaacudy.udytils.state.UpdatableState<T>` to represent data that is loaded asynchronously or the progress of asynchronous actions (e.g. the state of a "save" action might be represented as `AsyncState<Unit>`)
    * **Note**: Never directly construct `AsyncState.Loading`, `AsyncState.Success`, or `AsyncState.Error`. Use `AsyncState.fromSuspending` or `AsyncState.fromFlow` instead.
* **Rule**: ViewModel State objects must not define custom sealed classes or sealed interfaces for loading/success/error states — use `AsyncState<T>` instead. Custom sealed types for async status duplicate `AsyncState` semantics and bypass its built-in exception handling.
* **Rule**: ViewModel State objects should be a "transparent container" for domain objects and must not map domain data into lossy UI-level concepts (e.g. mapping a User into a UserListItem)
* **Rule**: ViewModel State objects should include `init` blocks that enforce invariants (rules that must be true for the object to be considered "valid")
* **Rule**: Formatting and visual representation (e.g., string concatenation, date formatting, or resource resolution) must be handled by the [Screen](#421-screens) or specialized `@Composable` properties/functions
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

### **4.3 `data` package constructs**
#### **4.3.1 Repositories**
* **Definition**: A class that provides implementations for [Domain Interfaces](#411-domain-interfaces), providing the "edge" of the domain layer
* **Rule**: Repositories must be named `[Name]Repository`
* **Rule**: Repositories must be marked as `internal`
* **Rule**: Repositories must not implement [Domain Interfaces](#411-domain-interfaces) directly
* **Rule**: Repositories must expose [Domain Interfaces](#411-domain-interfaces) as `public val` properties
* **Note**: The property name must match the interface name using `lowerCamelCase` (e.g., `val createUser = CreateUser { ... }`)
* **Note**: Properties must be initialized immediately; they must not be `lazy` or use custom getters
* **Rule**: Repositories are forbidden from injecting [Domain Interfaces](#411-domain-interfaces)
* **Rule**: Repositories are forbidden from injecting other Repositories
* **Rule**: Repositories may inject [Services](#4331-services), [Storage](#4321-storage) objects, or database clients to fulfill their domain properties.
* **Example**:
```kotlin
internal class UserRepository(
    private val userService: UserService, // kRPC Service
    private val userStorage: UserStorage, // Local storage
) {
    val getUser = GetUser { id ->
        // Logic to fetch from DB or Service
        userService.fetchUser(id)
    }

    val deleteUser = DeleteUser { id ->
        userService.deleteUser(id)
    }
}
```

#### **4.3.2 `data.storage` package constructs**

##### **4.3.2.1 Storage classes**
* **Definition**: A class responsible for local data persistence and retrieval (e.g., local credentials, preferences, cached data on disk)
* **Rule**: Storage classes must be named `[Name]Storage`
* **Rule**: Storage classes must not be `data class` — they are stateful service-like classes, not data containers
* **Rule**: Storage classes must reside in the `data.storage` package
* **Rule**: Client-side Storage classes must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)
* **Rule**: Storage classes are forbidden from injecting [Domain Interfaces](#411-domain-interfaces), [Repositories](#431-repositories), or [Services](#4331-services)
* **Rule**: Storage classes exist at the bottom of the dependency hierarchy — they must only depend on platform primitives and infrastructure
* **Note**: Client-side Storage classes may be `expect`/`actual` classes when the underlying storage mechanism is platform-specific (e.g., Keychain on iOS, SharedPreferences on Android)
* **Example (client)**:
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
* **Example (server)**:
```kotlin
internal class SessionStorage(
    private val documentStore: DocumentStore,
) {
    fun getSessions(campaignId: Campaign.Id): Flow<List<SessionDocument>> { ... }
    suspend fun createSession(...): SessionDocument { ... }
}
```

##### **4.3.2.2 Entity / Document classes**
* **Definition**: Types that represent data as it is stored in a database, document store, or on disk, and/or define the serialization and collection configuration for persisted data.
* **Rule**: Entity/Document classes must be `data` classes (or `data` objects in the case of pass-through Document types) 
* **Rule**: Entity/Document types must reside in the `data.storage` package

###### **4.3.2.2.1 Entity classes**
* **Rule**: The `Entity` suffix should be used for data which is stored in a relational database (such as SQL)
* **Rule**: Entity classes must be suffixed with `Entity` (e.g. `[Name]Entity`)
* **Rule**: Entity classes must not exist in the `:api` module.

###### **4.3.2.2.2 Document classes**
* **Rule**: The `Document` suffix should be used for data which is stored in a document database format (such as Firestore) or for other non-relational storage (such as Android SharedPreferences or direct-to-disk file storage)
* **Rule**: Document classes must be suffixed with `Document` (e.g. `[Name]Document`)
* **Rule**: `platform.document.DocumentSerializer`, `platform.document.DocumentData`, and `platform.document.CollectionDefinition` must not be used in the `domain` package. Document serialization/collection definitions belong in `data.storage`.
* **Rule**: Document classes (both forms) may exist in the `:api` module's `data.storage` package when the collection definition needs to be shared between client and server.
* **Rule**: Document types take one of two forms:
    * **Specialized Document** — a `data class` ending in `Document` with a `companion object` that defines a `collection` property. Used when the persisted representation differs from the domain object.
    * **Pass-through Document** — a `data object` ending in `Document` that defines a `collection` property. Used when a domain object is serialized directly (no specialized fields). This provides a natural place to introduce a specialized Document class in the future if needed.
* **Example (pass-through Document — domain object serialized directly)**:
```kotlin
// feature.campaigns.data.storage.CampaignDocument.kt (:api or :server)
data object CampaignDocument {
    val collection = DocumentSerializer
        .create<Campaign>(
            serialize = { campaign -> DocumentData(...) },
            deserialize = { data -> Campaign(...) },
            migrations = emptyList(),
        )
        .asCollection(Campaign.COLLECTION_NAME)
}
```
* **Example (specialized Document — different fields from domain object)**:
```kotlin
// feature.sessions.data.storage.SessionDocument.kt (:server)
data class SessionDocument(
    val id: Session.Id,
    val title: String,
    val processingStatus: ProcessingStatus,
    // ...
) {
    companion object {
        val collection = DocumentSerializer
            .create<SessionDocument>(
                serialize = { ... },
                deserialize = { ... },
                migrations = emptyList(),
            )
            .asCollection(Session.COLLECTION_NAME)
    }
}
```

#### **4.3.3 `data.services` and `data.storage` isolation**
* **Rule**: `data.services` must not depend on `data.storage` and `data.storage` must not depend on `data.services`. These two packages are independent layers.
* **Rule**: Classes that need to bridge both `data.services` and `data.storage` (e.g. `[Name]ServiceImpl`, `[Name]Manager`, `[Name]Interceptor`) must reside in the top-level feature package (e.g. `feature.[name]`), not in either `data.services` or `data.storage`.

#### **4.3.4 `data.services` package constructs**
##### **4.3.4.1 Services**
* **Definition**: The kRPC contract (in `:api`) and its implementation (in `:server`).
* **Rule**: When creating new functionality, always implement services as kRPC services in the appropriate server module — do not build client-only local services.
* **Rule (`:api`)**: Services must be interfaces annotated with `@Rpc`.
* **Rule (`:api`)**: Service functions may use [Domain Objects](#413-domain-objects), primitives, or specialized `Request`/`Response` objects as parameters/return types.
* **Rule (`:api`)**: If specialized types are used, they must be defined within the `data.services` package and named relative to the service (e.g., `UserService.CreateRequest`).
* **Rule (`:api`)**: Service functions must expect errors to be propagated using thrown exceptions; a service return type should only ever represent a successful result.
    * **Note**: Known exceptions should be defined as their own type, these can be defined in the domain package if they are expected to be visible to domain interfaces or the UI, or they can be defined within the data.services package if they are expected to be handled at the data layer
    * **Note**: Known exceptions should be marked on the service using the `@Throws` annotation
    * **Note**: `@Throws` annotations on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException` (or a superclass such as `Exception`) in the exception list, as this is required for Kotlin/Native compilation
    * **Note**: Service functions may also expect generic/unknown errors (e.g. RuntimeException) to occur, but these do not need to be defined as their own type or marked in `@Throws`
* **Example**:
```kotlin
// feature.user.data.services.UserService.kt (:api)
@Rpc
interface UserService {
    suspend fun createUser(request: CreateRequest): User

    @Serializable
    data class CreateRequest(val name: String, val email: String)
}
```

##### **4.3.4.2 Tool classes**
* **Definition**: A class extending `AssistantTool` that exposes a service operation as a function callable by the AI assistant.
* **Rule**: Tool classes must extend `AssistantTool`.
* **Rule**: Tool classes must be named `[Action][Entity]Tool` (e.g., `CreateArticleTool`).
* **Rule**: Tool classes must reside in `data.services.tools` of the feature whose service they primarily use.

##### **4.3.4.3 AssistantConfig subclasses**
* **Definition**: A class or object that extends `AssistantConfig<T>` to define the model name, system prompt, response schema, and response parser for a specific AI invocation performed by a service implementation.
* **Rule**: AssistantConfig subclasses must reside in the `data.services` package of the feature whose service uses them.
* **Rule**: AssistantConfig subclasses must be named `[Purpose]AssistantConfig` (e.g., `ChatAssistantConfig`, `SessionSummaryAssistantConfig`).
* **Rule**: Must not depend on `data.storage`.
* **Note**: Stateless configs — where no per-invocation state is needed — should be `object` declarations. Stateful configs — where per-request state must be tracked (e.g., capturing tool executions during a single generation) — should be `class` declarations and instantiated fresh per request.

### **4.4 top-level package constructs**
The top-level `feature.[name]` package in `:client` modules may contain:
* DI modules (see 4.4.1)

The top-level `feature.[name]` package in `:server` modules may contain:
* DI modules (see 4.4.1)
* Implementations of `Service` interfaces (see 4.3.4.1)

#### **4.4.1 Dependency modules**
* **Definition**: The configuration for Dependency Injection (DI) that wires the feature together
* **Rule**: DI modules must be defined in the top-level `feature.[name]` package of the `:client` and `:server` modules
* **Rule**: DI modules must be named `[name]Dependencies`; if the DI framework being used in the project uses classes, the first letter of `[name]` should be capitalised (e.g. `class [Name]Module`, if the DI framework being used in the project uses properties, the first letter of `[name]` should be lowercase (e.g. `val [name]Module`)
* **Rule**: The DI module for a feature must only bind/provide dependencies that are both defined and implemented in that feature
    * **Example**: It is forbidden for "featureA" to implement and bind a domain interface that is defined by "featureB"
* **Rule**: DI bindings must use the constructor reference style: `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`
* **Note**: It is the responsibility of `:app` level modules (application shells) to collect all of the DI modules provided by feature modules and create the final dependency graph
* **Note**: When a new dependency module is added, it must be registered in both `:app:client` and `:app:server`
* **Note**: When a new Service is added, it must be registered in `:app:server`

#### **4.4.2 Service implementations (`:server` only)**
* **Definition**: Implementations of `Service` interfaces (see 4.3.4.1) defined in `feature.[name].data.services` 
* **Rule (`:server`)**: For a service named `[name]Service` the service implementation should be suffixed with `Impl` (e.g. `[name]ServiceImpl`)
* **Rule (`:server`)**: Service implementations must be `internal`
* **Rule (`:server`)**: Service implementations must be defined in the top-level package for the feature (e.g. `feature.[name]`)
* **Rule (`:server`)**: Service implementations are forbidden from injecting [Domain Interfaces](#411-domain-interfaces)
* **Example**:
```kotlin
// feature.user.UserServiceImpl.kt (:server)
internal class UserServiceImpl(
  private val userStorage: UserStorage
) : UserService {
  override suspend fun createUser(request: UserService.CreateRequest): User {
    return userStorage.insertUser(
      user = UserEntity(
        name = request.name,
        email = request.email,
      )
    )
  }
}
```

---

## **5. Project-Wide Code Rules**

### **5.1 Exception handling**
* **Rule**: `try/catch` blocks must never catch `Exception`. Use `catch (t: Throwable)` or catch a specific exception type instead.
    * **Why**: kRPC deserializes server-side exceptions into types that may not extend `Exception`. A `catch (Exception)` block will silently miss these, causing errors to propagate uncaught and crash on internal threads instead of being handled by application code.
    * **Note**: On the client side, prefer `AsyncState.fromSuspending` over manual `try/catch` — it handles exceptions correctly and integrates with the ViewModel state pattern.
    * **Note**: Catching a specific exception type (e.g. `catch (t: IllegalArgumentException)`) is always acceptable when you only want to handle that specific case.
* **Rule**: Exception types defined in `data.services` (e.g. `CampaignService.UserNotFoundException`) must be annotated with `@Serializable` so that kRPC can transmit them to the client with the correct type and message.

---

## **6. Architecture Exceptions**

Architecture rules are enforced by Konsist-based tests in `:platform:common:architecture`. When a specific declaration cannot conform to the rules (e.g. a transitional class whose ideal location hasn't been determined yet), it can be added to `ArchitectureExceptions.kt` so the tests pass while the exception is tracked explicitly.

### **6.1 How to add an exception**
Add the fully-qualified class name to the `classes` list in `ArchitectureExceptions.kt`, with a comment explaining **why** the exception exists:

```kotlin
object ArchitectureExceptions {
    val classes = listOf(
        /**
         * The SessionProcessingManager serves as a helper for the SessionServiceImpl, to reduce
         * the lines of code in that file. <name>Manager is not a good name for a class. We have
         * not yet figured out the right way to classify these types of helper classes in the
         * architecture, so we're going to ignore this for now and revisit this at a later date.
         */
        "feature.sessions.SessionProcessingManager"
    )
}
```

The `isIgnored` function is called from specific tests to skip matching declarations. Currently it supports class-level exceptions via `KoClassDeclaration.fullyQualifiedName`.

### **6.2 Rules for adding exceptions**

* **Rule**: Architecture exceptions must only be added after discussing the exception with a human author. Adding an exception is an acknowledgement that the code does not currently conform to the architecture, and requires human judgement to determine whether the exception is acceptable.
* **Rule**: Adding an architecture exception is **not** a valid way to resolve an immediate architecture test failure without user feedback. If a test fails, the correct first step is to fix the code or update the architectural rules — not to suppress the failure with an exception.
* **Rule**: Every exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and what the intended resolution is.
* **Rule**: Exceptions should be treated as temporary. They should be revisited periodically and removed once the underlying issue is resolved.