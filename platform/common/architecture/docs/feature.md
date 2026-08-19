> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/feature/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Feature Rules](../src/main/kotlin/architecture/rules/feature/FeatureRules.kt)

A feature has a client implementation and a server implementation that communicate only through
the RPC contract, and share one vocabulary:

```
client.ui → client.domain ← client.data → [ contract ] ← server.services → server.domain ← server.data
```

The client and server have the same layer structure: a [UseCase](clientdomain.md#use-case) over
[domain interfaces](clientdomain.md#domain-interface) answered by a
[Repository](clientdata.md#repository) on the client has the same shape as a
[UseCase](serverdomain.md#use-case) over [domain interfaces](serverdomain.md#domain-interface)
answered by a [Repository](serverdata.md#repository) on the server. Neither imports the other:
the network is the only connection between client and server, and `:api` is the only channel
between features.

The feature **root** is `feature.[name]`, with no `client` or `server` segment. The Gradle
module it is compiled into decides what it holds: in `:api` it is the feature's shared
vocabulary; in `:client` and `:server` the same package name holds DI wiring.

**In `:api`, the root holds the shared vocabulary**: the domain models, exceptions, and
constants that both the client and the server use. It is common Kotlin that compiles for every
target.

The root also shows, straight off the package path, how far a change reaches. A
[shared domain model](#shared-domain-model) is used by the client, the server, and potentially
other features, so renaming a field is a compatibility event. A
[domain model](clientdomain.md#domain-model) in
[`client.domain`](clientdomain.md#domain-model) or
[`server.domain`](serverdomain.md#domain-model) is used only within the client or server that
defines it, and refactors freely. The `domain` layers build on the root: their models compose
the shared ones.

> **A change here is a compatibility event.** These types are serialized across the network, so
> renaming a field, changing a type, or moving a sealed variant breaks compatibility across
> features. A PR touching a feature root should be reviewed as one.

The root holds domain objects and validation only: no interfaces with behaviour, no use cases, no
logic beyond validating the values it carries. Anything with behaviour belongs on a side —
single-function contracts are [domain interfaces](clientdomain.md#domain-interface) in
`client.domain` or [`server.domain`](serverdomain.md#domain-interface).

**In `:client` and `:server`, the same package holds the feature's wiring.** It is reserved for
dependency injection: Koin modules that define the feature's DI bindings, wiring its
[ViewModels](clientui.md#view-model), Repositories
([client](clientdata.md#repository), [server](serverdata.md#repository)),
[UseCases](clientdomain.md#use-case) ([server](serverdomain.md#use-case)),
[StorageClasses](serverdata.md#storage-class), and
[Service](serverservices.md#service-interface) implementations into the graph. Concrete classes
(ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.

##### Constructs

* [Shared Domain Model](#shared-domain-model)
* [Shared Exception](#shared-exception)
* [Shared Constants](#shared-constants)
* [Shared Extension Function](#shared-extension-function)
* [Shared Extension Property](#shared-extension-property)
* [Dependency Module](#dependency-module)
* [Dependency Module Helper](#dependency-module-helper)

##### Rules

* A feature root may import feature roots only, never a declaration from inside a side
    * **Why:** The root is shared vocabulary: both sides depend on it, so it can depend on neither. An import of `client.**` would make the type unusable on the server, and an import of `server.**` would drag persistence or wire machinery into a type the client compiles.
    * **Note:** Other features' roots are importable — real vocabularies reference each other. Keep that graph acyclic.
    * **Note:** Tested as an allow-list: a `feature.` import is a root import when everything between the feature name and the imported declaration is a type name, so anything sitting in a deeper package is side-private whatever that package is called.
    * **Note:** Scoped to `:api`, where the vocabulary lives; the same package name on `:client`/`:server` holds the feature's DI module, whose whole job is to name both sides' implementations.
* A feature root must not contain platform-specific dependencies, such as Android, Compose, Ktor, or SQL
    * **Why:** The root is common Kotlin consumed by every target and by the server. A platform import here would break compilation for some target or drag transport/persistence machinery into the vocabulary itself.
    * **Note:** Scoped to `:api`, where the vocabulary lives; the same package name on `:client`/`:server` holds the feature's [dependency module](#dependency-module), which is out of scope because wiring a side necessarily names that side's platform types.
* A feature root must declare only domain objects, constants, validation, and pure extensions over them
    * **Why:** Behaviour in the root would be shared between client and server, which is exactly what the taxonomy forbids — only vocabulary is shared. A single-function interface belongs in a side's `domain` ([client](clientdomain.md#domain-interface), [server](serverdomain.md#domain-interface)), where that side's adapter provides it.
    * **Note:** Enforced by the Constructs: a declaration in the root matching none of them fails the membership rule.
    * **Enforced by:** `architecture.everyDeclarationBelongsToALayer`
* A feature root type that participates in polymorphic serialization must pin an explicit `@SerialName`
    * **Note:** Enforced project-wide by `ProjectRules.serialNamePinnedOnPolymorphicTypes`; restated here because the root is where the wire vocabulary lives.
    * **Enforced by:** `ProjectRules.serialNamePinnedOnPolymorphicTypes`
* A DI binding must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`
    * **Why:** The reference style lets Koin validate the constructor parameters against the graph at startup; the lambda style hides missing or cyclic dependencies until the first injection at runtime.

---

## [Shared Domain Model](../src/main/kotlin/architecture/rules/feature/SharedDomainModel.kt)

An immutable `@Serializable` type in the feature root: a business object or concept both sides
speak. It is the feature's vocabulary in its purest form — the highest level of abstraction it
has for saying what it does. Because both sides name it and it is serialized across the network,
every field is part of a compatibility surface.

The side-private counterpart is the [domain model](clientdomain.md#domain-model)
([server](serverdomain.md#domain-model)), which refactors freely because nothing outside its side
can observe the change. Same word, one qualifier: `Shared` is what says both sides name it, and
the package is where that is written down. A side-private model may serialize too — for a column
or for restored state — so `@Serializable` is what a shared model needs, not what distinguishes
it.

* **Note:** Nested types (enums, value classes, sealed interfaces/classes) belong nested only
  when conceptually inseparable from the parent, such as `User.Id` or `Transport.Car.FuelType`
  in the examples below. Otherwise, model them as their own shared domain models.

##### Requirements

* A Shared Domain Model resides in the feature root package `feature.[name]`
* A Shared Domain Model is declared in the feature's `:api` module
* A Shared Domain Model is a class or interface
* A Shared Domain Model satisfies one of: {is `sealed`, is a `data class`, is an `enum class`, is a `value class`}
* A Shared Domain Model is annotated with `@Serializable`

##### Rules

* A shared domain model must be immutable (val properties only)

##### Guidance

* A shared domain model should use nested value classes for identifiers where appropriate
* A shared domain model should use sealed interface hierarchies to model polymorphic data where appropriate
* A shared domain model should include `init` blocks that enforce invariants
* A shared domain model should use nested types when conceptually inseparable from the parent

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
    @SerialName("Transport.Car")
    data class Car(
        override val id: String,
        override val name: String,
        val fuelType: FuelType,
    ) : Transport {
        @Serializable
        enum class FuelType {
            Petrol,
            Diesel,
            Electric,
            Hydrogen,
        }
    }

    @Serializable
    @SerialName("Transport.Bicycle")
    data class Bicycle(
        override val id: String,
        override val name: String,
        val type: Type,
    ) : Transport {
        @Serializable
        enum class Type {
            Manual,
            Electric,
        }
    }

    @Serializable
    @SerialName("Transport.Bus")
    data class Bus(
        override val id: String,
        override val name: String,
        val routeId: String,
    ) : Transport
}
```

---

## [Shared Exception](../src/main/kotlin/architecture/rules/feature/SharedException.kt)

A class representing a known failure mode that both sides name: thrown by a server
implementation, carried across the service boundary, and matched by client code. Because it
crosses the wire, it is part of the feature's shared language and lives in the root.

* **Note:** A shared exception must be listed in `@Throws` on the primary function of every
  [domain interface](clientdomain.md#domain-interface)
  ([server](serverdomain.md#domain-interface)) that raises it.
* **Note:** `@Serializable` is part of what a shared exception *is* — a failure mode that
  cannot be serialized cannot arrive on the other side, and
  `ProjectRules.serviceExceptionsSerializable` holds the same line inside a service contract's
  reach. An exception that is not wire-visible is side-private: it belongs in that side's
  `domain`, not in the root.

##### Requirements

* A Shared Exception resides in the feature root package `feature.[name]`
* A Shared Exception is declared in the feature's `:api` module
* A Shared Exception is a class extending RuntimeException/Exception/PresentableException
* A Shared Exception is annotated `@Serializable`

---

## [Shared Constants](../src/main/kotlin/architecture/rules/feature/SharedConstants.kt)

An `object` declaration whose only members are `val` constants. It holds the feature's magic
numbers, lookup tables, and named tags — values both sides need to agree on.

* **Note:** A constants object is the right home for values such as `val MAX_PARTY_SIZE = 6`
  or a lookup table. Anything with behaviour belongs on a
  [shared domain model](#shared-domain-model) as a member or extension.

##### Requirements

* A Shared Constants resides in the feature root package `feature.[name]`
* A Shared Constants is declared in the feature's `:api` module
* A Shared Constants is an `object` with only `val` properties and no functions

---

## [Shared Extension Function](../src/main/kotlin/architecture/rules/feature/SharedExtensionFunction.kt)

A top-level extension function on a [shared domain model](#shared-domain-model) that adds
derived or convenience behavior, such as `User.isAdult()`. Pure over its inputs — it computes
from the object's values and touches nothing else.

* **Note:** The explicit receiver is what makes it an extension of the vocabulary rather than
  free-standing behaviour. A top-level function with no receiver is logic, and logic lives on a
  side.
* **Note:** Convenience logic for a domain interface belongs as default member functions on the
  [interface](clientdomain.md#domain-interface) ([server](serverdomain.md#domain-interface))
  itself. Extension functions are for adding behavior to shared domain models.

##### Requirements

* A Shared Extension Function resides in the feature root package `feature.[name]`
* A Shared Extension Function is declared in the feature's `:api` module
* A Shared Extension Function declares an explicit extension receiver
* A Shared Extension Function has receiver/return/parameter types that are shared domain models, primitives, or collections of those

##### Rules

* A shared extension function must not introduce platform-specific dependencies
    * **Enforced by:** `FeatureRules.noPlatformDeps`

---

## [Shared Extension Property](../src/main/kotlin/architecture/rules/feature/SharedExtensionProperty.kt)

A top-level extension property on a [shared domain model](#shared-domain-model) that exposes
derived state.

* **Note:** The same constraints as [shared extension functions](#shared-extension-function)
  apply, including the explicit receiver: a top-level property with no receiver is state or
  configuration, not vocabulary. Prefer a property when the value is a pure projection of the
  receiver and is cheap to compute on every read.

##### Requirements

* A Shared Extension Property resides in the feature root package `feature.[name]`
* A Shared Extension Property is declared in the feature's `:api` module
* A Shared Extension Property declares an explicit extension receiver
* A Shared Extension Property has a receiver/type that is a shared domain model, primitive, or collection of those

---

## [Dependency Module](../src/main/kotlin/architecture/rules/feature/DependencyModule.kt)

The configuration for Dependency Injection (DI) that wires the feature together.

* **Note:** The naming convention is `[name]ClientDependencies` in `:client` and
  `[name]ServerDependencies` in `:server`. The Construct enforces the `Dependencies` suffix;
  the `Client`/`Server` infix is convention.
* **Note:** The `:app` modules (application shells) are responsible for collecting the DI
  modules provided by feature modules into the final dependency graph. When a new dependency
  module is added, it must be registered in both `:app:client:common` and `:app:server`; when
  a new Service is added, it must be registered in `:app:server`.

##### Requirements

* A Dependency Module resides in the top-level `feature.[name]` package of a `:client` or `:server` module
* A Dependency Module is a property
* A Dependency Module is named `[Name]Dependencies`

##### Rules

* A Dependency Module must only bind/provide dependencies that are both defined and implemented in its own feature
    * **Why:** If feature A binds an implementation of feature B's domain interface, feature B's DI graph silently depends on feature A, and removing or refactoring A breaks B's wiring at runtime rather than at compile time. Each feature owns its own bindings; cross-feature consumption goes through `:api` interfaces only.
* A Dependency Module registers a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block
    * **Note:** `bindService` (from `dev.isaacudy.udytils.urpc.koin`) registers the binding under its own concrete type, bound to `UrpcService`, with the impl resolved lazily.
    * **Note:** Never use `scoped<UrpcService> { [Name]ServiceUrpcBinding { get() } }`: every such binding shares the `UrpcService` definition key, so co-registered services override each other and `getAll<UrpcService>()` returns only one. The test catches this form.
    * **Note:** `urpcService(::[Name]ServiceUrpcBinding)` is the equivalent standalone form when there is no impl definition to chain off.

##### Examples

Registering a urpc service in `:server`, per `FeatureRules.DependencyModule.urpcServiceBinding`:

```kotlin
scope<UrpcCall> {
    scopedOf(::UserProfileServiceImpl)
        .bind(UserProfileService::class)
        .bindService(::UserProfileServiceUrpcBinding)
}
```

Service implementations live in `feature.[name].server.services`, never in the top-level package; only their bindings appear in the dependency module:

```kotlin
// feature.user.server.services.UserServiceImpl.kt (:server)
internal class UserServiceImpl(
    private val createUser: CreateUser,
    private val getUser: GetUser,
) : UserService { /* … */ }
```

---

## [Dependency Module Helper](../src/main/kotlin/architecture/rules/feature/DependencyModuleHelper.kt)

An `internal` function with a Koin `Module` receiver that a `Dependencies` module calls to
register a group of bindings. Used to split a large module into readable, named chunks.

##### Requirements

* A Dependency Module Helper is a function
* A Dependency Module Helper is `internal`
* A Dependency Module Helper has a Koin `Module` receiver
* A Dependency Module Helper resides in the top-level `feature.[name]` package of a `:client` or `:server` module, beside the `Dependencies` module it splits
