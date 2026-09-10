> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/clientdomain/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Client Domain](../src/main/kotlin/architecture/rules/clientdomain/ClientDomain.kt)

`feature.[name].client.domain` — the client's internal domain layer. This package contains
single-function [domain interfaces](clientdomain.md#domain-interface) that the client's
[ViewModels](clientui.md#view-model) consume and [Repositories](clientdata.md#repository)
provide, and [domain models](#domain-model) that never leave the client. The layer may include
[UseCases](#use-case) when multiple domain interfaces need to be composed,
[extension functions](#extension-function) and [extension properties](#extension-property) that
add derived behaviour to a domain model, and [constants](#constants) objects that hold shared
constant values.

It is **pure**: it may import feature roots and nothing else. No Compose, no Ktor, no
persistence, no service contracts. That purity is what makes it testable without a harness, and
what stops client abstractions leaking into the wire vocabulary.

A domain interface may be **published to `:api`** when another feature's UI needs it; use cases
and domain models stay in `:client`. Publishing is moving the file, not changing the package.

This layer has the same construct names and rules as [`server.domain`](serverdomain.md). The
layer supplies the context, so the names never repeat it.

##### Constructs

* [Domain Interface](#domain-interface)
* [Use Case](#use-case)
* [Domain Model](#domain-model)
* [Extension Function](#extension-function)
* [Extension Property](#extension-property)
* [Constants](#constants)
* [Workflow](#workflow)
* [Workflow Step](#workflow-step)
* [Domain Exception](#domain-exception)

##### Rules

* The `client.domain` layer must import feature roots and `:api`-published `client.domain` declarations only
    * **Why:** Domain sits between the UI and the data adapter and knows neither. Importing `client.data` inverts the dependency, importing `client.ui` cycles it, and importing `server.**` breaks the side boundary outright.
    * **Note:** Other features' client.domain interfaces and models are importable when their declaration resides in `:api`; implementations are never published, so they are never importable across features.
    * **Note:** A file's own feature's `client.domain` is the layer itself, so it is not an import out of the layer; the exemption is scoped to the importing file's feature and to no other.
* The `client.domain` layer must not contain platform-specific dependencies, such as Android, Compose, Ktor, or SQL
    * **Why:** The layer stays pure Kotlin so it compiles for every KMP target and stays unit-testable. Expose a domain interface and implement it in `client.data` instead.
    * **Note:** A generated Exposed table (`platform.server.postgres.tables.**`) counts as a platform dependency — naming one is naming a column, whatever the package reads as. So do the project's UI-carrying platform modules (`platform.design.**`, `platform.ui.**`): their types are Compose-backed. Pure cross-cutting primitives from other platform modules — a logger, an auth credential — are legitimate here.
* The `client.domain` layer may depend on another feature's root, but only via that feature's `:api` module
    * **Enforced by:** `ModuleRules.clientApiOnly`, `ModuleRules.crossFeatureCodeViaApi`
* A `client.domain` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Note:** A subsystem package is a capability of the feature that nothing outside it names, and it is never published (`ModuleRules.subsystemsNotPublished`): the constructs classify inside one exactly as they do at the layer root, because a subsystem is a location rather than a kind of thing.
    * **Note:** Composition across two subsystems belongs to their shared ancestor, where a shared payload is an ordinary [domain model](#domain-model) and a shared contract an ordinary [domain interface](#domain-interface).
    * **Enforced by:** `ProjectRules.subsystemVisibility`

##### Guidance

* A feature's `client.domain` layer should hold domain interfaces in proportion to its domain models and consumers, not one per storage call or field
    * **Note:** The audit reports one line per feature: domain interfaces (published ones counted separately), domain models in `client.domain` and the feature root, UseCases, and how many interfaces have no production consumer, one, or several. Many interfaces beside few models, or a high one-consumer share, marks the feature for the domain contract inventory in `ukpt-architecture-review`.
    * **Note:** A consumer is a class whose primary constructor takes the interface; DI modules and tests are not consumers.
    * **Audited:** a test reports non-conforming code without ever failing.

---

## [Domain Interface](../src/main/kotlin/architecture/rules/clientdomain/DomainInterface.kt)

A `fun interface` that represents a piece of domain-level business logic.

* **Note:** Default functions should use expressive names. They should provide commonly used
  functionality, such as handling a particular exception type, or simplify calling the primary
  function with particular parameters.
* **Note:** Implementations must never override an interface's default functions. Convenience
  functions belong as default members, not top-level extensions, so they stay discoverable and
  co-located with the interface.
* **Note:** Generic or unknown errors don't need their own exception type or `@Throws` entry.

##### Requirements

* A Domain Interface resides in `feature..client.domain..`
* A Domain Interface is a `fun interface`
* A Domain Interface has a primary function that is an `operator fun invoke`
* A Domain Interface declares all functions as `suspend` or returning a `Flow<T>`
* A Domain Interface is prefixed with `FlowOf` when its primary function returns a `Flow`

##### Rules

* A Domain Interface's primary-function parameters must be shared domain models, the layer's own domain models, nested types, primitives, standard date/time value types, collections of those, or a `Flow` of those
* A Domain Interface's primary-function return type must be shared domain models, the layer's own domain models, nested types, primitives, standard date/time value types, collections of those, a `Flow` of those, or no value
* A Domain Interface's functions must propagate errors via thrown exceptions, never via the return type
    * **Why:** A result type that carries the failure makes every caller unwrap it, and the layer's vocabulary grows a wrapper around each contract. Thrown exceptions keep the primary function's return type the thing it produces.
    * **Note:** Known exceptions should be their own type extending RuntimeException, marked with `@Throws`.
    * **Note:** `@Throws` on a `suspend` function must include `kotlin.coroutines.cancellation.CancellationException` (or a superclass such as `Exception`): an interface published to `:api` compiles for every target, and kotlinc rejects the function on iOS without it.
* A Domain Interface must be implemented by a Repository (as a property) or by a UseCase
    * **Note:** The test accepts either a class whose parents include the interface (a UseCase) or a `[Name]Repository` with a property that references the interface.

##### Guidance

* A Domain Interface may define additional default functions that call the primary function
* A Domain Interface should name a capability a consumer asks for, or return a domain model a consumer needs; it should not mirror one storage call, one property of a domain model, or one step of an implementation
    * **Why:** A consumer that injects several storage-shaped interfaces and joins their results reconstructs a domain model the owning Repository could have produced; the join, and the knowledge of which storage produces each part, then repeats in every consumer.
    * **Note:** Before adding a Domain Interface, name its consumer, the domain result it returns, its provider, and its reason to exist apart from the interfaces beside it. Several interfaces added together for one consumer are a candidate for one Repository property returning one domain model.
    * **Note:** An implementation step with one caller is a private function, a file-private function, or a nested class of that caller, not a Domain Interface.
    * **Note:** Assembling a domain model from storage the feature owns does not permit reading another feature's storage, injecting a sibling Repository, or holding a Domain Interface inside a domain model.
    * **Note:** The audit reports a class injecting six or more domain interfaces, grouped by provider; a group of three or more interfaces from one provider with one consumer that mixes reads and other operations; and a `Get<Model><Part>` or `FlowOf<Model><Part>` name where `Model` is a domain model. Each is a review question with its evidence, not a defect.
    * **Audited:** a test reports non-conforming code without ever failing.
* When a consumer needs several facts about one domain model at once, and those facts share scope, freshness, and failure behaviour, a Domain Interface should return one immutable domain model carrying all of them
    * **Why:** One read returns one snapshot. Several reads assembled by the consumer return facts from different moments, and every consumer decides for itself how a partially loaded model behaves.
    * **Note:** Reads stay separate when a consumer uses one of them alone, when their authorization, freshness, failure, or lifecycle differs, or when one is optional and its failure must not fail the other. Appearing on the same Screen is not a reason to combine reads.
    * **Note:** Returning one data class does not by itself make its facts consistent. When the consumer needs one consistent snapshot, the provider uses one query, one transaction at a suitable isolation level, a shared lock, or a revision, and preserves authorization and tenant scope across every constituent read.
    * **Note:** Queries over one collection that differ only in their filter share one Domain Interface: a nested `sealed interface Input` carries the variants and a default function per variant keeps call sites flat.
    * **Note:** A domain model with lifecycle states is a sealed hierarchy whose variants carry the values each state requires, in place of nullable properties and Booleans that are meaningful only in combination.
    * **Note:** The audit reports three or more reads from one provider whose only consumer is one class. Constructor injection is the evidence, so the finding asks whether the reads are collected together; it does not prove they are.
    * **Audited:** a test reports non-conforming code without ever failing.
* When several mutations act on one domain model and share a return type, prefer a single `Update[Noun]` interface over one interface per mutation: a nested `sealed interface Update` carries the variants, the abstract `invoke(id, update)` is the single entry point, and default functions (`title(...)`, `addMember(...)`) keep call sites flat. When publishing through `:api`, publish exactly the capability another feature needs, never the whole mutation family.
    * **Note:** Reads do not join an update family: a read returns the domain model it produces, and reads a consumer needs together form one read projection.
    * **Note:** The audit reports three or more mutations on one noun from one provider, by name (`CreateTeam`, `UpdateTeam`, `DeleteTeam`). Whether they share a return type, and so form one family, is the reviewer's call.
    * **Audited:** a test reports non-conforming code without ever failing.
* A Domain Interface should be injected by at least one production class
    * **Note:** A DI module binds an interface without consuming it, and a test fake is not a consumer. An interface published through `:api` may be consumed from another feature's module, which the audit sees when that module is in scope.
    * **Note:** The audit counts two forms of consumption: a class taking the interface as a primary-constructor parameter, and a file resolving it out of the Koin container with `get<T>()`, `inject<T>()`, or `koinInject<T>()` — the form an app module's startup wiring or a Compose entry point uses. A `bind T::class` in a module is the binding, not a consumer, and is not counted.
    * **Note:** A consumer that reaches the interface in neither form — through reflection, or a lambda parameter typed elsewhere — is not counted, so an interface reported here is a question to check rather than proven dead code.
    * **Audited:** a test reports non-conforming code without ever failing.
* A mutation should return the value its caller needs next, and no value when an observed read projection already carries the outcome
    * **Why:** A caller that receives an identifier and reads the model back performs a second read for a value the producer had in hand. A caller that receives a value it never uses carries a contract with no consumer.
    * **Note:** A returned value describes the state captured within the mutation, including whether the mutation was accepted; it does not imply the state is unchanged after the mutation completes.

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
        return invoke(Input.AllUsers)
    }

    fun nameContains(searchTerm: String): Flow<List<User>> {
        return invoke(Input.NameContains(searchTerm = searchTerm))
    }

    fun isFriendOf(userId: String): Flow<List<User>> {
        return invoke(Input.FriendOf(userId = userId))
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

A domain interface that returns a computed read projection, combining several sources into one consistent snapshot. Compose in a UseCase when the combination is read-model logic; compose in a Repository when it is one data source's atomic projection. Do not build one projection holding unrelated optional facilities — live polling and optional resources may stay separate when their failure should not make the screen unusable.

```kotlin
fun interface FlowOfUserProfile {
    operator fun invoke(userId: User.Id): Flow<UserProfile>
}
```

---

Reads that one consumer always uses together, before: one interface per fact, and the consumer joins them.

```kotlin
fun interface FlowOfCheckoutItems {
    operator fun invoke(cartId: CartId): Flow<List<CartItem>>
}

fun interface FlowOfCheckoutShipping {
    operator fun invoke(cartId: CartId): Flow<ShippingOption?>
}

fun interface FlowOfCheckoutTotal {
    operator fun invoke(cartId: CartId): Flow<Money>
}
```

After: one read projection returned by the Repository that owns the cart, and the three interfaces above are gone.

```kotlin
data class Checkout(
    val items: List<CartItem>,
    val shipping: ShippingOption?, // null: no option chosen yet
    val total: Money,
)

fun interface FlowOfCheckout {
    operator fun invoke(cartId: CartId): Flow<Checkout>
}
```

`FlowOfCheckoutPromotions` stays a separate interface: promotions are optional, polled on their own schedule, and their failure must not fail the checkout.

---

## [Use Case](../src/main/kotlin/architecture/rules/clientdomain/UseCase.kt)

A class that implements a single [domain interface](#domain-interface).

* **Note:** Immutable helper properties, such as loggers, are permitted. "No mutable state"
  forbids `var` properties, not properties in general.
* **Note:** If a UseCase only injects a single other domain interface, consider whether that
  logic should become a default function of the other domain interface instead.
* **Note:** When breaking down a complex UseCase, use file-private extension functions,
  private functions, or nested classes instead of additional domain interfaces or UseCases.
* **Note:** A phase of an orchestration with one caller is a private function of that caller.
  It becomes a UseCase of its own when a second caller needs it on its own.

##### Requirements

* An Use Case resides in `feature..client.domain..`
* An Use Case is a non-sealed/data/enum/value class named `[DomainInterface]Impl`
* An Use Case implements exactly one domain interface

##### Rules

* A UseCase must not contain mutable state: all properties must be `val`
    * **Why:** A UseCase instance is shared by its consumers and may be invoked concurrently; a `var` property lets one invocation change another's behaviour or internal state.
* A UseCase must not override any default function of its domain interface
    * **Why:** The only abstract member of a domain interface is the primary `operator fun invoke`; every other function is a default. Default functions are contract behaviour built on `invoke`; overriding one makes the same helper behave differently depending on which implementation is injected.
* A UseCase in the same module and package as its domain interface must be declared in the interface's file
    * **Why:** A UseCase is the implementation of one interface, and a reader of either needs the other. Two files named `X` and `XImpl` in one package separate a contract from its only implementation and double the file count of the package. An interface published to `:api` is in a different module from its implementation, so those two are separate files by construction.
    * **Note:** The parent is resolved through the UseCase file's imports and matched against the client's classified domain interfaces by fully-qualified name. Module and package are compared per source set, so an implementation in a platform source set of the interface's module, which cannot share the interface's file, is not asked to.

##### Guidance

* A UseCase may inject domain interfaces to perform its logic
* A UseCase that becomes too complex should be broken into private, file-private, or nested parts
* A UseCase should exist for a decision, or for a composition of capabilities that exist independently of it, not to forward one call
    * **Why:** A UseCase over one domain interface adds a class, a binding, and a contract between the caller and that one dependency; the same logic as a default function of the dependency's interface, or as the dependency's own Repository property, adds none of them.
    * **Note:** The audit reports a UseCase whose primary constructor takes exactly one domain interface of its side. Authorization wrappers and error translation are the usual reasons such a UseCase stays.
    * **Audited:** a test reports non-conforming code without ever failing.

##### Examples

A UseCase exists for a decision over capabilities that exist independently of it; the Repository stores what it is told. It shares its interface's file when both are in the same module and package; the implementation of an interface published to `:api` has its own file in the client module.

```kotlin
// feature/shop/client/domain/Reorder.kt
package feature.shop.client.domain

fun interface Reorder {
    suspend operator fun invoke(id: OrderId)
}

internal class ReorderImpl(
    private val getOrder: GetOrder,
    private val updateCart: UpdateCart,
) : Reorder {
    override suspend fun invoke(id: OrderId) {
        val order = getOrder(id) ?: throw OrderNotFoundException()
        val available = order.lines.filter { it.stillAvailable }
        if (available.isEmpty()) throw NothingToReorderException()
        updateCart.addLines(available)
    }
}
```

An implementation step with one caller is a private function of that caller, not a further domain interface.

```kotlin
// feature/shop/client/domain/RefreshCartPrices.kt
package feature.shop.client.domain

internal class RefreshCartPricesImpl(
    private val getCart: GetCart,
    private val getPrices: GetPrices,
    private val updateCart: UpdateCart,
) : RefreshCartPrices {
    override suspend fun invoke(cartId: CartId) {
        val cart = getCart(cartId) ?: return
        val prices = getPrices(cart.lines.map { it.productId })
        updateCart.prices(cartId, reprice(cart, prices))
    }

    private fun reprice(cart: Cart, prices: Map<ProductId, Money>): List<CartLine> { ... }
}
```

---

## [Domain Model](../src/main/kotlin/architecture/rules/clientdomain/DomainModel.kt)

A model that belongs to one side only: a draft being edited, a cursor, an in-flight state
machine, a computed projection, a payload written to a column.

The contrast with a [shared domain model](feature.md#shared-domain-model) is what the package
split encodes, and it is **residence and reach** rather than shape. A shared domain model is part
of the feature's shared vocabulary, named by both the client and server and readable by other
features, so renaming a field is a cross-feature compatibility event. A domain model is private
to the client: nothing outside the client can observe a change, so it refactors freely.

Serialization does not decide which of the two a type is. A domain model may carry
`@Serializable` — a payload persisted in a column, state restored across a process death — and
what that costs is a migration for its own stored data, never a cross-feature compatibility
event.

The **network** is what decides: a model the server receives is no longer client-private, and
belongs in the feature root with the compatibility obligations that come with it.

##### Requirements

* A Domain Model resides in `feature..client.domain..`
* A Domain Model is a class or interface
* A Domain Model satisfies one of: {is `sealed`, is a `data class`, is an `enum class`, is a `value class`}

##### Rules

* A domain model must be immutable — no `var` properties
    * **Why:** Mutable state in the domain layer makes results depend on the order of earlier calls, and the layer untestable in isolation.
* A domain model must not hold a domain interface: no property or constructor parameter whose type is a domain interface of the same client or server `domain` layer, bare, nullable, or inside a wrapper such as `Lazy<…>` or `List<…>`
    * **Why:** A domain model is data; a property typed as a domain interface is a dependency that is supplied at the model's construction site rather than injected, so Koin's startup validation does not see it and the class that consumes the model has a dependency its constructor does not declare.
    * **Note:** A property type is resolved through its file's imports and matched against the client's classified domain interfaces by fully-qualified name.
* A domain model that needs to cross the network belongs in the feature root instead
    * **Note:** Crossing the network is the test, not carrying `@Serializable`: a payload a StorageClass writes into a column, or a state a client restores after a process death, is serialized and still private to the client or server that owns it.
    * **Note:** Persistence is `server.data`'s concern: a model that is stored but not shared is mapped to a [storage record](serverdata.md#storage-record) there, not promoted to the root.
    * **Verification:** not automatically verifiable; enforced by review.
* A domain model must not re-implement a concept a shared domain model already defines; use or compose the shared model instead
    * **Note:** The feature's vocabulary has one source of truth in the root; a private copy of a concept drifts from it as both change.
    * **Verification:** not automatically verifiable; enforced by review.

##### Guidance

* A typed configuration value for a UseCase is a domain model of its side, assembled in the dependency module and injected
    * **Note:** A setting that varies between deployments reaches a UseCase as a field of an immutable data class the dependency module constructs (`single { ReconciliationConfig(cleanupTimeout = 15.seconds) }`), never as a constructor default (`ProjectRules.injectableConstructorsHaveNoDefaults`). A setting fixed for every deployment is a private property of the UseCase.
    * **Note:** The data layer's counterpart is a [configuration](clientdata.md#configuration): a `[Name]Config` data class beside the Repository or adapter it configures.

##### Examples

A computed read projection that groups related domain objects into a single consistent snapshot. The projection preserves domain objects rather than flattening to display strings; the domain interface that produces it groups by consistency and failure boundary, not by screen.

```kotlin
// feature/shop/client/domain/UserProfile.kt
package feature.shop.client.domain

data class UserProfile(
    val user: User,
    val memberships: List<Membership>,
    val permissions: Set<Permission>,
)
```

---

A domain model that holds domain interfaces — a violation:

```kotlin
// feature/shop/client/domain/CheckoutInputs.kt
package feature.shop.client.domain

data class CheckoutInputs(
    val getShippingOptions: GetShippingOptions,
    val calculateTotal: Lazy<CalculateTotal>,
)
```

The corrected form when the consumer needs the capabilities: it injects each interface directly.

```kotlin
// feature/shop/client/ui/CheckoutViewModel.kt
package feature.shop.client.ui

class CheckoutViewModel(
    private val getShippingOptions: GetShippingOptions,
    private val calculateTotal: CalculateTotal,
) : ViewModel() { ... }
```

The corrected form when the consumer needs the values: one domain interface returns a model carrying them, and the consumer injects that one interface.

```kotlin
// feature/shop/client/domain/CheckoutInputs.kt
package feature.shop.client.domain

data class CheckoutInputs(
    val shippingOptions: List<ShippingOption>,
    val total: Money,
)

fun interface FlowOfCheckoutInputs {
    operator fun invoke(cartId: CartId): Flow<CheckoutInputs>
}
```

---

## [Extension Function](../src/main/kotlin/architecture/rules/clientdomain/ExtensionFunction.kt)

A top-level extension function in `client.domain` that adds derived behaviour to a
[domain model](#domain-model) or a [shared domain model](feature.md#shared-domain-model). Pure
over its inputs — it computes from the receiver's values and touches nothing else. The
counterpart, one level deeper, of a
[shared extension function](feature.md#shared-extension-function): same shape, client-private
receiver.

* **Note:** The explicit receiver is what makes it an extension of the layer's vocabulary rather
  than free-standing behaviour. A top-level function with no receiver is logic, and logic here is
  a [domain interface](#domain-interface) with a [UseCase](#use-case) behind it.
* **Note:** Convenience logic for a domain interface belongs as a default member function on the
  interface, where it stays co-located with the contract it simplifies.
* **Note:** A helper that only one UseCase needs stays `private` inside that UseCase's file, per
  `ClientDomain.UseCase.breakDownComplexUseCases`. This construct is for an extension the layer
  shares.

##### Requirements

* An Extension Function resides in `feature..client.domain..`
* An Extension Function declares an explicit extension receiver
* An Extension Function has receiver/return/parameter types that are domain models, primitives, or collections of those

---

## [Extension Property](../src/main/kotlin/architecture/rules/clientdomain/ExtensionProperty.kt)

A top-level extension property in `client.domain` that exposes derived state on a
[domain model](#domain-model) or a [shared domain model](feature.md#shared-domain-model).

* **Note:** The same constraints as an [extension function](#extension-function) apply, including
  the explicit receiver: a top-level property with no receiver is state or configuration, not
  vocabulary. Prefer a property when the value is a pure projection of the receiver and is cheap
  to compute on every read.

##### Requirements

* An Extension Property resides in `feature..client.domain..`
* An Extension Property declares an explicit extension receiver
* An Extension Property has a receiver/type that is a domain model, primitive, or collection of those

---

## [Constants](../src/main/kotlin/architecture/rules/clientdomain/Constants.kt)

An `object` in `client.domain` whose only members are `val` constants: the caps, thresholds and
named tags the client's logic agrees on. The client-private counterpart of
[shared constants](feature.md#shared-constants) — a value both the client and server have to
agree on belongs in the feature root instead.

* **Note:** Anything with behaviour is not a constants object. A pure computation over a model
  belongs on it as an [extension function](#extension-function), and anything that composes
  contracts is a [UseCase](#use-case).

##### Requirements

* A Constants resides in `feature..client.domain..`
* A Constants is an `object` with only `val` properties and no functions
* A Constants does not satisfy: is named `[Name]Workflow`

---

## [Workflow](../src/main/kotlin/architecture/rules/clientdomain/Workflow.kt)

An `object` named `[Name]Workflow` holding the definition of a multi-step process: the `Step`
contract its steps implement, the vocabulary those steps hand values through, and the pure
function that orders them.

A workflow exists when a process is described by **data rather than by a call sequence**. Its
steps declare what they need and what they produce; the workflow reads those declarations and
derives an order. That is what separates it from a [UseCase](#use-case) that calls three
contracts in a row — a UseCase *is* the sequence, and changing the order means editing it, while
a workflow's order falls out of what its steps say about themselves.

What lives inside the object is the **definition**, and only the definition: the `Step`
interface, the typed keys and registry its steps exchange values through, the context passed
down the chain, and pure functions over that vocabulary. Everything that *does* something is a
top-level declaration another construct governs — the [steps](#workflow-step) themselves, and
the [UseCase](#use-case) that injects them, calls `resolve`, and runs the plan.

* **Note:** Nesting is what makes the definition readable as one unit — a reader opens one file
  and sees the whole vocabulary. It is also the one place the catalog's membership rule does not
  reach, which is why `nestsOnlyDefinition` exists: nesting is for definition, never a way to
  keep behaviour out of the catalog's sight.
* **Note:** The ordering function stays on the object while it is pure and dependency-free. The
  day it needs a collaborator is the day it becomes a [domain interface](#domain-interface) with
  a [UseCase](#use-case) behind it, like anything else with a dependency.
* **Note:** A second workflow is the signal to lift the shared key/registry machinery into
  `:platform`. One workflow does not make a framework.

##### Requirements

* A Workflow resides in `feature..client.domain..`
* A Workflow is an object
* A Workflow is named `[Name]Workflow`

##### Rules

* A Workflow must nest a `Step` contract
    * **Why:** The `Step` interface is what makes the object a workflow rather than a namespace: it is the contract the process is assembled from. An object named `[Name]Workflow` that declares no `Step` is a misnamed constants holder, and saying so is more useful than leaving it unclassified.
* A Workflow's `Step` contract must declare the metadata the workflow composes by
    * **Why:** A workflow derives its order from what each step says about itself, so the contract has to carry that as data. A `Step` whose only members are functions can only be hand-sequenced, which is the thing a workflow exists not to be.
    * **Note:** Typical members are `requires` and `produces`, stated over the workflow's nested artifact vocabulary; another workflow may compose by something else.
* A Workflow must nest only its definition: no `suspend` function on the object itself or on a nested class
    * **Why:** The membership rule classifies top-level declarations only, so anything nested inside an object is invisible to the catalog. That is correct for a definition and dangerous for anything else: a Repository or a UseCase nested here would answer to no construct at all. Behaviour stays at the top level where a construct governs it.
    * **Note:** `suspend` is this codebase's marker for reaching outside the process, so it is what separates a definition from work. The nested `Step` contract is exempt — declaring suspending work is exactly its job; performing it is the step implementation's, at the top level.
    * **Note:** What a nested declaration may *hold* is left to the layer's `pure` rule, which already forbids this layer the adapters a hidden Repository or UseCase would need. This rule holds the line that matters here: nothing nested inside a workflow does work.
* A Workflow's own vocabulary must be immutable: no `var` properties and no mutable collection types
    * **Why:** The object is a single shared instance read by every step. State that changes on it would make one run observable from another.

---

## [Workflow Step](../src/main/kotlin/architecture/rules/clientdomain/WorkflowStep.kt)

A top-level class implementing a [Workflow](#workflow)'s nested `Step` contract — one unit of a
declared process, in its own file.

A step is an adapter, and a thin one. It reads its inputs from the workflow's context, calls the
[domain interfaces](#domain-interface) that do the real work, and writes its outputs back. What
makes it a step rather than a [UseCase](#use-case) is that it **declares** its inputs and
outputs instead of being called in a fixed position: the workflow reads those declarations and
decides when it runs.

Steps live at the top level, not nested in the workflow object, precisely so the catalog
governs them. The workflow holds the definition; the steps are the behaviour.

* **Note:** Name a step for what it does — `[Verb]Step`, as in `ValidateStep` or
  `SubmitStep`. The suffix is what a reader scans for; the verb is what they read.
* **Note:** A step that needs another step's output declares the artifact, never the step. That
  is the whole mechanism — declaring the dependency is what lets the workflow order the two, and
  naming the sibling directly is how a workflow decays back into a call sequence.

##### Requirements

* A Workflow Step resides in `feature..client.domain..`
* A Workflow Step is a class
* A Workflow Step implements a `[Name]Workflow`'s nested `Step` contract

##### Rules

* A WorkflowStep must not inject another step
    * **Why:** A step that holds another step calls it directly, which puts the order back in the code and takes it away from the declarations. Dependencies between steps are expressed as artifacts the workflow resolves.
* Only a Workflow's composing UseCase may take steps as dependencies
    * **Why:** A step is meaningful only in the order its workflow derives. A ViewModel or an unrelated UseCase that injects one calls it out of that order, in a position nothing declared and the workflow cannot see — which is how half a process ends up running somewhere else.
    * **Note:** The composer is the `[Interface]Impl` UseCase that injects the steps, asks the workflow to order them, and runs the plan — so the exemption is an `Impl` in the side's `domain` layer, where UseCases live. A ServiceImpl or any other outer-layer class holding a step is reported.

##### Guidance

* A WorkflowStep should be named for the work it does, as `[Verb]Step`

---

## [Domain Exception](../src/main/kotlin/architecture/rules/clientdomain/DomainException.kt)

A class named `[Name]Exception` representing a failure mode the client names and handles on
its own — a cache that cannot be read, a draft that will not restore.

The counterpart of a [shared exception](feature.md#shared-exception), which is the same idea one
level up: a failure both the client and server name, thrown by a server implementation and
matched by client code, living in the feature root because it crosses the wire. A domain
exception does not cross anything. Nothing outside the client can observe it, so it refactors
freely.

`SharedException` already draws the line: an exception that is not wire-visible belongs in
`client.domain` or `server.domain`, not in the root. This is that home.

* **Note:** A failure a [domain interface](#domain-interface) documents belongs in its `@Throws`,
  whichever of the two kinds it is.

##### Requirements

* A Domain Exception resides in `feature..client.domain..`
* A Domain Exception is named `[Name]Exception`
* A Domain Exception is a class extending RuntimeException/Exception
