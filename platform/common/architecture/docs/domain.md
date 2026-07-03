> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/domain/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Domain Layer](../src/main/kotlin/architecture/rules/domain/DomainLayer.kt)

The `domain` axis is the deepest layer of a feature and appears in all three modules: `:api`,
`:client`, and `:server`. Its contents are pure Kotlin: data models
([domain objects](#domain-object)) and single-function interfaces
([domain interfaces](#domain-interface)). `domain` depends on no other axis, and every other
axis depends on it. On the client, [Repositories](data.md#repository) implement the domain
interfaces that [ViewModels](ui.md#view-model) consume; on the server, the
[`services` axis](services.md) implements them.

The `domain` package must only contain [domain interfaces](#domain-interface),
[domain objects](#domain-object), [UseCases](#use-case),
[domain exceptions](#domain-exception), [domain constants](#domain-constants),
[domain extension functions](#domain-extension-function), and
[domain extension properties](#domain-extension-property).

The [Rules](#rules) below apply across the whole `feature.[name].domain` package.

* **Note:** Cross-feature domain dependencies are permitted, because real-world domains depend
  on each other, but they should be kept to a minimum. Get the direction of each dependency
  right and avoid circular dependencies.

##### Constructs

* [Domain Interface](#domain-interface)
* [Domain Object](#domain-object)
* [Use Case](#use-case)
* [Domain Exception](#domain-exception)
* [Domain Constants](#domain-constants)
* [Domain Extension Function](#domain-extension-function)
* [Domain Extension Property](#domain-extension-property)

##### Rules

* The `domain` layer must not contain platform-specific dependencies, such as Android, Ktor, or SQL
    * **Why:** The domain layer stays pure Kotlin so it can be used in `:client`, `:server`, and every KMP target, and stays unit-testable. Expose a domain interface and implement it in `data` or `services` instead.
* The `domain` layer must not depend on `ui`, `data`, or `services` packages within the feature
    * **Why:** The dependency graph is `ui → domain ← data`, with `services` depending on domain. Importing those into domain would invert the graph or create a cycle.
* The `domain` layer may depend on another feature's `domain` only via that feature's `:api` module
    * **Enforced by:** `ModuleRules.clientApiOnly`, `ModuleRules.serverApiOnly`, `ModuleRules.crossFeatureCodeViaApi`

---

## [Domain Interface](../src/main/kotlin/architecture/rules/domain/DomainInterface.kt)

A `fun interface` that represents a piece of domain-level business logic.

* **Note:** Default functions should use expressive names. They should provide commonly used
  functionality, such as handling a particular exception type, or simplify calling the primary
  function with particular parameters.
* **Note:** Implementations must never override an interface's default functions. Convenience
  functions belong as default members, not top-level extensions, so they stay discoverable and
  co-located with the interface.
* **Note:** Generic or unknown errors don't need their own exception type or `@Throws` entry.

##### Requirements

* A Domain Interface resides in `feature..domain..`
* A Domain Interface is a `fun interface`
* A Domain Interface has a primary function that is an `operator fun invoke`
* A Domain Interface declares all functions as `suspend` or returning a `Flow<T>`
* A Domain Interface is prefixed with `FlowOf` when its primary function returns a `Flow`

##### Rules

* A Domain Interface's primary-function parameters must be domain objects, nested types, primitives, or collections of those
* A Domain Interface's primary-function return type must be domain objects, nested types, primitives, collections of those, or no value
* A Domain Interface must be implemented by a Repository (as a property) or by a UseCase
    * **Note:** The test accepts either a class whose parents include the interface (a UseCase) or a `[Name]Repository` with a property that references the interface.
* A Domain Interface's functions must propagate errors via thrown exceptions, never via the return type
    * **Why:** `@Throws` on a `suspend` function must include `CancellationException` (or a superclass such as `Exception`); without it, kotlinc rejects the function on iOS targets.
    * **Note:** Known exceptions should be their own type extending RuntimeException, marked with `@Throws`.
    * **Note:** `@Throws` on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException`.

##### Guidance

* A Domain Interface may define additional default functions that call the primary function

##### Examples

Domain interfaces showing `@Throws` exceptions, `Flow` returns (the `FlowOf` prefix), and default convenience functions:

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

---

## [Domain Object](../src/main/kotlin/architecture/rules/domain/DomainObject.kt)

An immutable type that represents domain-level data.

* **Note:** Nested types (enums, value classes, sealed interfaces/classes) belong nested only
  when conceptually inseparable from the parent, such as `User.Id` or `Transport.Car.FuelType`
  in the examples below. Otherwise, model them as their own domain objects.

##### Requirements

* A Domain Object resides in `feature..domain..`
* A Domain Object is a class or interface
* A Domain Object satisfies one of: {is `sealed`, is a `data class`, is an `enum class`, is a `value class`}
* A Domain Object is annotated with `@Serializable`

##### Rules

* A Domain Object must be immutable (val properties only)

##### Guidance

* A Domain Object should use nested value classes for identifiers where appropriate
* A Domain Object should use sealed interface hierarchies to model polymorphic data where appropriate
* A Domain Object should include `init` blocks that enforce invariants
    * **Audited:** a test reports non-conforming code without ever failing.
* A Domain Object should use nested types when conceptually inseparable from the parent

##### Examples

Domain objects showing a nested value-class ID, an `init` invariant, and a sealed hierarchy with nested types:

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

---

## [Use Case](../src/main/kotlin/architecture/rules/domain/UseCase.kt)

A class that implements a single [domain interface](#domain-interface).

* **Note:** Immutable helper properties, such as loggers, are permitted. "No mutable state"
  forbids `var` properties, not properties in general.
* **Note:** If a UseCase only injects a single other domain interface, consider whether that
  logic should become a default function of the other domain interface instead.
* **Note:** When breaking down a complex UseCase, use file-private extension functions,
  private functions, or nested classes instead of additional domain interfaces or UseCases.

##### Requirements

* An Use Case resides in `feature..domain..`
* An Use Case is a non-sealed/data/enum/value class named `[DomainInterface]Impl`
* An Use Case implements exactly one domain interface

##### Rules

* A UseCase must not contain mutable state: all properties must be `val`
* A UseCase must not override any default function of its domain interface
    * **Why:** The only abstract member of a domain interface is the primary `operator fun invoke`; every other function is a default. Overriding a default in an implementation defeats the purpose of the interface helpers.

##### Guidance

* A UseCase may inject domain interfaces to perform its logic
* A UseCase that becomes too complex should be broken into private, file-private, or nested parts

---

## [Domain Exception](../src/main/kotlin/architecture/rules/domain/DomainException.kt)

A class that represents a known failure mode raised by a domain interface.

* **Note:** A domain exception lives at the top of the `domain` package when it is shared
  between multiple domain interfaces, or as a nested class on the
  [domain interface](#domain-interface) that throws it. It must be listed in `@Throws` on the
  throwing interface's primary function.

##### Requirements

* A Domain Exception resides in `feature..domain..`
* A Domain Exception is a class extending RuntimeException/Exception/PresentableException

---

## [Domain Constants](../src/main/kotlin/architecture/rules/domain/DomainConstants.kt)

An `object` declaration whose only members are `val` constants. It holds domain-level magic
numbers, lookup tables, and named tags.

* **Note:** A constants object is the right home for values such as `val MAX_PARTY_SIZE = 6`
  or a lookup table. Anything with behaviour belongs on a domain object as a member or
  extension.

##### Requirements

* A Domain Constants resides in `feature..domain..`
* A Domain Constants is an `object` with only `val` properties and no functions

---

## [Domain Extension Function](../src/main/kotlin/architecture/rules/domain/DomainExtensionFunction.kt)

A top-level extension function on a domain object that adds derived or convenience
behavior.

* **Note:** Prefer default member functions on [domain interfaces](#domain-interface) for
  domain-interface convenience logic. Extension functions are appropriate for adding behavior
  to domain objects, such as `User.isAdult()`.

##### Requirements

* A Domain Extension Function resides in `feature..domain..`
* A Domain Extension Function has receiver/return/parameter types that are domain objects, primitives, or collections of those

##### Rules

* A Domain Extension Function must not introduce platform-specific dependencies
    * **Enforced by:** `DomainLayer.noPlatformDeps`

---

## [Domain Extension Property](../src/main/kotlin/architecture/rules/domain/DomainExtensionProperty.kt)

A top-level extension property on a domain object that exposes derived state.

* **Note:** The same constraints as [domain extension functions](#domain-extension-function)
  apply. Prefer a property when the value is a pure projection of the receiver and is cheap
  to compute on every read.

##### Requirements

* A Domain Extension Property resides in `feature..domain..`
* A Domain Extension Property has a receiver/type that is a domain object, primitive, or collection of those
