> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Narrative sources: the `DomainLayer*.md` fragments in `src/test/kotlin/architecture/rules/domain/`; structure and rule content come from the rule catalog.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

# The `domain` layer

The `domain` axis is the deepest layer of a feature and appears in all three modules — `:api`, `:client`, and `:server`. Its contents are pure Kotlin: data models ([domain objects](#domain-objects)) and single-function interfaces, sometimes called Interactors ([domain interfaces](#domain-interfaces)). `domain` is the centre of gravity on both sides of the wire: it depends on no other axis, and every other axis depends on it — on the client, [Repositories](data.md#repositories) implement the domain interfaces that [ViewModels](ui.md#viewmodels) consume; on the server, the [`services` axis](services.md) implements them.

The `domain` package must only contain [domain interfaces](#domain-interfaces), [domain objects](#domain-objects), [UseCases](#usecases), [domain exceptions](#domain-exceptions), [domain constants](#domain-constants), [domain extension functions](#domain-extension-functions), and [domain extension properties](#domain-extension-properties).

The [Layer rules](#layer-rules) below apply across the whole `feature.[name].domain` package.

* **Note**: Cross-feature domain dependencies should be minimised where possible, but are permitted because real-world domains have genuine dependencies between them. The important thing is getting the direction of dependencies correct and avoiding circular dependencies.

## Layer rules

* **`DomainLayer.noPlatformDeps`** `✅ tested` — Domain must not contain platform-specific dependencies (Android, Ktor, SQL, …)
    * **Why**: The domain layer stays pure Kotlin so it ports across :client/:server and every KMP target and stays unit-testable. Expose a domain interface and implement it in `data`/`services`.
* **`DomainLayer.noUiDataServicesDeps`** `✅ tested` — Domain must not depend on `ui`, `data`, or `services` packages within the feature
    * **Why**: The dependency graph is `ui → domain ← data`, with `services` depending on domain. Importing those into domain would invert the graph or create a cycle.
* **`DomainLayer.crossFeatureViaApi`** `✅ tested` — May depend on another feature's `domain` only via that feature's `:api` module
    * **Enforced by**: `ModuleRules.clientApiOnly`, `ModuleRules.serverApiOnly`

## Domain interfaces

* **Definition**: A functional interface representing domain-level functionality/business logic.
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

* **Construct** `DomainLayer.DomainInterface` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..domain..`
    * Domain interfaces must be a `fun interface`
    * The primary function of a domain interface must be an `operator fun invoke`
    * All functions in a domain interface must be `suspend` or return a `Flow<T>`
    * Flow-returning domain interfaces are prefixed with `FlowOf`
* **Rules**:
    * **`DomainLayer.DomainInterface.interfaceDefaults`** `📋 guidance` — May define additional default functions that call the primary function
    * **`DomainLayer.DomainInterface.primaryParameterTypes`** `📋 guidance` — Primary-function parameters must be domain objects, nested types, primitives, or collections of those
    * **`DomainLayer.DomainInterface.primaryReturnType`** `📋 guidance` — Primary-function return type must be domain objects, nested types, primitives, collections of those, or no value
    * **`DomainLayer.DomainInterface.implementedByRepositoryOrUseCase`** `📋 guidance` — Must be implemented by a Repository (as a property) or by a UseCase
    * **`DomainLayer.DomainInterface.errorsViaExceptions`** `✅ tested` — Functions propagate errors via thrown exceptions, never via the return type
        * **Why**: @Throws on suspend functions must include CancellationException (or a superclass like Exception) — required for Kotlin/Native: kotlinc rejects the function on iOS targets otherwise.
        * **Note**: Known exceptions should be their own type extending RuntimeException, marked with `@Throws`.
        * **Note**: `@Throws` on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException`.

## Domain objects

* **Definition**: An immutable type representing data at the domain-level.
* **Note**: Nested types (enums, value classes, sealed interfaces/classes) belong nested only when conceptually inseparable from the parent — like `User.Id` or `Transport.Car.FuelType` below; otherwise model them as their own domain objects.
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

* **Construct** `DomainLayer.DomainObject` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..domain..`
    * is a class or interface
    * one of {is `sealed`, is a `data class`, is an `enum class`, is a `value class`}
    * Domain objects must be annotated with `@Serializable`
* **Rules**:
    * **`DomainLayer.DomainObject.immutable`** `✅ tested` — Domain objects must be immutable (val properties only)
    * **`DomainLayer.DomainObject.nestedValueClassIds`** `📋 guidance` — Should use nested value classes for identifiers where appropriate
    * **`DomainLayer.DomainObject.sealedHierarchies`** `📋 guidance` — Should use sealed interface hierarchies to model polymorphic data where appropriate
    * **`DomainLayer.DomainObject.invariantInitBlocks`** `📋 guidance` — Should include `init` blocks that enforce invariants
    * **`DomainLayer.DomainObject.nestedTypes`** `📋 guidance` — Should use nested types when conceptually inseparable from the parent

## UseCases

* **Definition**: A class that implements a single [domain interface](#domain-interfaces).
* **Note**: Immutable helper properties (e.g., loggers) are permitted — "no mutable state" forbids `var` properties, not properties in general.
* **Note**: If a UseCase only injects a single other domain interface, consider whether that logic should become a default function of the other domain interface instead.
* **Note**: When breaking down a complex UseCase, reach for file-private extension functions, private functions, or nested classes — not additional domain interfaces/UseCases that pollute the namespace.

* **Construct** `DomainLayer.UseCase` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..domain..`
    * A UseCase is a non-sealed/data/enum/value class named `[DomainInterface]Impl`
    * A UseCase must implement exactly one domain interface
* **Rules**:
    * **`DomainLayer.UseCase.noMutableState`** `✅ tested` — A UseCase must not contain mutable state — all properties are `val`
    * **`DomainLayer.UseCase.noOverridingDefaults`** `✅ tested` — Must not override any default function of its domain interface
        * **Why**: The only abstract member is the primary `operator fun invoke`; every other function is a default. Overriding a default per-implementation defeats the point of the interface helpers.
    * **`DomainLayer.UseCase.mayInjectDomainInterfaces`** `📋 guidance` — May inject domain interfaces to perform its logic
    * **`DomainLayer.UseCase.breakDownComplexUseCases`** `📋 guidance` — If it becomes too complex, break it into private/file-private/nested parts

## Domain exceptions

* **Definition**: A class that represents a known failure mode raised by a domain interface.
* **Note**: Domain exceptions live at the top of the `domain` package when shared between multiple domain interfaces, or as a nested class on the [domain interface](#domain-interfaces) that throws them; they must be listed in `@Throws` on the throwing interface's primary function.

* **Construct** `DomainLayer.DomainException` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..domain..`
    * A domain exception is a class extending RuntimeException/Exception/PresentableException

## Domain constants

* **Definition**: An `object` declaration whose only members are `val` constants — used to anchor domain-level magic numbers, lookup tables, or named tags.
* **Note**: A constants object is the right home for things like `val MAX_PARTY_SIZE = 6` or a sealed-but-keyed lookup table. Anything that wants behaviour belongs on a domain object as a member or extension.

* **Construct** `DomainLayer.DomainConstants` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..domain..`
    * Domain constants are an `object` with only `val` properties and no functions

## Domain extension functions

* **Definition**: A top-level extension function on a domain object that adds derived or convenience behavior.
* **Note**: Prefer default member functions on [domain interfaces](#domain-interfaces) for domain-interface convenience logic. Extension functions are appropriate for adding behavior to domain objects (e.g., `CampaignRole.permissions()`).

* **Construct** `DomainLayer.DomainExtensionFunction` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..domain..`
    * Receiver/return/parameter types are domain objects, primitives, or collections of those
* **Rules**:
    * **`DomainLayer.DomainExtensionFunction.noPlatformDeps`** `📋 guidance` — Domain extension functions must not introduce platform-specific dependencies

## Domain extension properties

* **Definition**: A top-level extension property on a domain object that exposes derived state.
* **Note**: Same constraints as [domain extension functions](#domain-extension-functions). Prefer a property when the value is a pure projection of the receiver and is cheap to compute on every read.

* **Construct** `DomainLayer.DomainExtensionProperty` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..domain..`
    * Receiver/type is a domain object, primitive, or collection of those
