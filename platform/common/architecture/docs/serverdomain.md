> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/serverdomain/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Server Domain](../src/main/kotlin/architecture/rules/serverdomain/ServerDomain.kt)

`feature.[name].server.domain` — the server's internal domain layer. This package contains
single-function [domain interfaces](#domain-interface) that the server's
[Services](serverservices.md#service-interface) consume and
[Repositories](serverdata.md#repository) provide, and [domain models](#domain-model) that never
leave the server. The layer may include [UseCases](#use-case) when multiple domain interfaces
need to be composed, [extension functions](#extension-function) and
[extension properties](#extension-property) that add derived behaviour to a domain model, and
[constants](#constants) objects that hold shared constant values.

`server.services` and `server.data` never import each other: services consume the domain's
interfaces, Repositories implement them by reading through the
[StorageClasses](serverdata.md#storage-class) that own the tables, and an
[IntegrationClient](serverdata.md#integration-client) implements one when the data comes from
outside the process rather than a table.

A domain interface may be **published to `:api`** when another feature needs it; use cases and
domain models stay in `:server`. Publishing is moving the file, not changing the package. There
is no separate "operation" or "query" concept — whether a contract crosses a feature boundary
is decided by which module its file sits in.

Because this layer imports feature roots only, a domain interface cannot touch a table and
cannot inject request-scoped authentication. A storage function reached from `services` is
expressed here as a domain interface, or folded with its siblings into a [UseCase](#use-case)
when the logic spans several.

This layer has the same construct names and rules as [`client.domain`](clientdomain.md).

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

* The `server.domain` layer must import feature roots and `:api`-published `server.domain` declarations only
    * **Why:** The domain layer imports neither of its neighbouring layers. Importing `server.data` would couple logic to persistence; importing `server.services` would drag the wire contract and request scope into it, and would let a domain interface reach the layer that is supposed to consume it.
    * **Note:** This is what makes `noTables` and `noAuth` unnecessary as separate rules — neither is reachable from here.
    * **Note:** Other features' roots are importable — real vocabularies reference each other. Another feature's server.domain interfaces and models are importable when their declaration resides in `:api`; implementations are never published, so they are never importable across features.
    * **Note:** A file's own feature's `server.domain` is the layer itself, so it is not an import out of the layer; the exemption is scoped to the importing file's feature and to no other.
* The `server.domain` layer must not contain persistence or transport dependencies, such as Exposed, Ktor, or SQL
    * **Why:** Pure logic stays testable without a database or a request. Declare a domain interface and let `server.data` satisfy it.
    * **Note:** A generated Exposed table (`platform.server.postgres.tables.**`) counts as a persistence dependency — naming one is naming a column, whatever the package reads as. The project's UI-carrying platform modules (`platform.design.**`, `platform.ui.**`) count too. Pure cross-cutting primitives from other platform modules — a logger, an auth credential, `platform.server.postgres.TransactionRunner` — are legitimate here.
* A `server.domain` interface that another feature calls must be declared in the `:api` module
    * **Note:** Publishing is moving the file between modules — the package is unchanged, so no import churn.
    * **Note:** The layer root is the whole of the publication channel: a subsystem declaration is never published (`ModuleRules.subsystemsNotPublished`). A capability a subsystem computes that another feature needs is restated as a root contract the subsystem satisfies.
    * **Enforced by:** `ModuleRules.crossFeatureCodeViaApi`
* A `server.domain` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Note:** A subsystem is a capability the feature has that nothing outside it names — the framework of a processing pipeline, an audio subtree. It earns a package at the point where a reader scanning the layer root has to skip past it, and the constructs classify inside one exactly as they do at the root: a subsystem is a location, not a kind of thing.
    * **Note:** Composition across two subsystems belongs to their shared ancestor, where a shared payload is an ordinary [domain model](#domain-model) and a shared contract an ordinary [domain interface](#domain-interface).
    * **Enforced by:** `ProjectRules.subsystemVisibility`

##### Guidance

* A feature's `server.domain` layer should hold domain interfaces in proportion to its domain models and consumers, not one per storage call or field
    * **Note:** The audit reports one line per feature: domain interfaces (published ones counted separately), domain models in `server.domain` and the feature root, UseCases, and how many interfaces have no production consumer, one, or several. Many interfaces beside few models, or a high one-consumer share, marks the feature for the domain contract inventory in `ukpt-architecture-review`.
    * **Note:** A consumer is a class whose primary constructor takes the interface; DI modules and tests are not consumers.
    * **Audited:** a test reports non-conforming code without ever failing.

---

## [Domain Interface](../src/main/kotlin/architecture/rules/serverdomain/DomainInterface.kt)

A `fun interface` that represents a piece of domain-level business logic.

A storage-backed interface — one a [Repository](serverdata.md#repository) provides over the
tables its StorageClasses own — is **transaction-joining by default**: called inside a
`platform.server.postgres.TransactionRunner.inTransaction` block, its writes are part of that
transaction and commit or roll back with the rest of the block. That is what makes a published
interface composable: a [UseCase](#use-case) in another feature can put one write beside its own
and have the pair land together, without either feature naming the other's tables. An interface
an [IntegrationClient](serverdata.md#integration-client) provides is not — it reaches outside the
process, and belongs outside the block entirely.

* **Note:** Default functions should use expressive names. They should provide commonly used
  functionality, such as handling a particular exception type, or simplify calling the primary
  function with particular parameters.
* **Note:** Implementations must never override an interface's default functions. Convenience
  functions belong as default members, not top-level extensions, so they stay discoverable and
  co-located with the interface.
* **Note:** Generic or unknown errors don't need their own exception type or `@Throws` entry.

##### Requirements

* A Domain Interface resides in `feature..server.domain..`
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
* A Domain Interface must be provided as a property by a Repository or an IntegrationClient, or implemented by a UseCase
    * **Why:** Domain sits between `server.services` and `server.data` and is satisfied from the far side: a [Repository](serverdata.md#repository) exposes the interface as a `public val` over the tables its StorageClasses own, an [IntegrationClient](serverdata.md#integration-client) does the same over something outside the process, or a UseCase implements it by composing several others. An interface with none of the three is a contract nothing answers.
    * **Note:** The test accepts either a class whose parents include the interface (a UseCase, or an IntegrationClient that satisfies it directly) or a `[Name]Repository`/`[Name]Client`/`[Name]Provider` with a property that references it.

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
    * **Note:** The audit reports interfaces no class injects through its primary constructor. A consumer outside a constructor, such as an app-module lambda or a top-level function, is not counted.
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

A read whose result has lifecycle states returns a sealed domain model; each variant carries the values that state requires, so no consumer checks nullable properties against Booleans.

```kotlin
sealed interface OrderState {
    val id: OrderId

    data class Draft(override val id: OrderId, val lines: List<OrderLine>) : OrderState
    data class Submitted(override val id: OrderId, val lines: List<OrderLine>, val payment: Payment) : OrderState
    data class Cancelled(override val id: OrderId, val reason: String) : OrderState
}

fun interface GetOrderState {
    suspend operator fun invoke(id: OrderId): OrderState?
}
```

The [Repository examples](serverdata.md#repository) show the consumer before and after the Repository assembles such a model.

---

## [Use Case](../src/main/kotlin/architecture/rules/serverdomain/UseCase.kt)

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

* An Use Case resides in `feature..server.domain..`
* An Use Case is a non-sealed/data/enum/value class named `[DomainInterface]Impl`
* An Use Case implements exactly one domain interface

##### Rules

* A UseCase must not contain mutable state: all properties must be `val`
    * **Why:** A UseCase instance is shared by its consumers and may be invoked concurrently; a `var` property lets one invocation change another's behaviour or internal state.
* A UseCase must not override any default function of its domain interface
    * **Why:** The only abstract member of a domain interface is the primary `operator fun invoke`; every other function is a default. Default functions are contract behaviour built on `invoke`; overriding one makes the same helper behave differently depending on which implementation is injected.
* A UseCase in the same module and package as its domain interface must be declared in the interface's file
    * **Why:** A UseCase is the implementation of one interface, and a reader of either needs the other. Two files named `X` and `XImpl` in one package separate a contract from its only implementation and double the file count of the package. An interface published to `:api` is in a different module from its implementation, so those two are separate files by construction.
    * **Note:** The parent is resolved through the UseCase file's imports and matched against the server's classified domain interfaces by fully-qualified name. Module and package are compared per source set, so an implementation in a platform source set of the interface's module, which cannot share the interface's file, is not asked to.

##### Guidance

* A UseCase may inject domain interfaces to perform its logic
* A UseCase that becomes too complex should be broken into private, file-private, or nested parts
* A UseCase should exist for a decision, or for a composition of capabilities that exist independently of it, not to forward one call
    * **Why:** A UseCase over one domain interface adds a class, a binding, and a contract between the caller and that one dependency; the same logic as a default function of the dependency's interface, or as the dependency's own Repository property, adds none of them.
    * **Note:** The audit reports a UseCase whose primary constructor takes exactly one domain interface of its side. Authorization wrappers and error translation are the usual reasons such a UseCase stays.
    * **Audited:** a test reports non-conforming code without ever failing.
* A UseCase must not call an IntegrationClient from inside a `TransactionRunner.inTransaction` block
    * **Note:** The block holds a pooled database connection, and any row locks it has taken, for as long as it runs — a network round trip inside it starves the pool for that whole time. Make the integration call first and open the transaction with its result in hand.
    * **Note:** A domain interface does not say what satisfies it, so read the wiring: an interface provided by an [IntegrationClient](serverdata.md#integration-client) is the one to keep outside.

##### Examples

A UseCase exists for a decision over capabilities that exist independently of it; the Repository stores what it is told. It shares its interface's file when both are in the same module and package; the implementation of an interface published to `:api` has its own file in the server module.

```kotlin
// feature/shop/server/domain/SubmitOrder.kt
package feature.shop.server.domain

fun interface SubmitOrder {
    suspend operator fun invoke(id: OrderId)
}

internal class SubmitOrderImpl(
    private val getOrder: GetOrder,
    private val updateOrder: UpdateOrder,
) : SubmitOrder {
    override suspend fun invoke(id: OrderId) {
        val order = getOrder(id) ?: throw OrderNotFoundException()
        if (order.lines.isEmpty() || order.payment == null) throw OrderIncompleteException()
        updateOrder.submitted(id)
    }
}
```

Phases of an orchestration with one caller are private functions of that caller, not further domain interfaces; phase order, failure isolation, and cancellation stay visible in one place.

```kotlin
// feature/shop/server/domain/ReconcileOrders.kt
package feature.shop.server.domain

internal class ReconcileOrdersImpl(
    private val getOrdersAwaitingReconciliation: GetOrdersAwaitingReconciliation,
    private val getPaymentRecord: GetPaymentRecord,
    private val updateOrder: UpdateOrder,
) : ReconcileOrders {
    override suspend fun invoke() {
        for (order in getOrdersAwaitingReconciliation()) {
            reconcilePayment(order)
            releaseExpiredHold(order)
        }
    }

    private suspend fun reconcilePayment(order: Order) { ... }

    private suspend fun releaseExpiredHold(order: Order) { ... }
}
```

---

## [Domain Model](../src/main/kotlin/architecture/rules/serverdomain/DomainModel.kt)

A model that belongs to one side only: a draft being edited, a cursor, an in-flight state
machine, a computed projection, a payload written to a column.

The contrast with a [shared domain model](feature.md#shared-domain-model) is what the package
split encodes, and it is **residence and reach** rather than shape. A shared domain model is part
of the feature's shared vocabulary, named by both the client and server and readable by other
features, so renaming a field is a cross-feature compatibility event. A domain model is private
to the server: nothing outside the server can observe a change, so it refactors freely.

Serialization does not decide which of the two a type is. A domain model may carry
`@Serializable` — a payload persisted in a column, state restored across a process death — and
what that costs is a migration for its own stored data, never a cross-feature compatibility
event.

The **network** is what decides: a model the client receives is no longer server-private, and
belongs in the feature root with the compatibility obligations that come with it.

##### Requirements

* A Domain Model resides in `feature..server.domain..`
* A Domain Model is a class or interface
* A Domain Model satisfies one of: {is `sealed`, is a `data class`, is an `enum class`, is a `value class`}

##### Rules

* A domain model must be immutable — no `var` properties
    * **Why:** Mutable state in the domain layer makes results depend on the order of earlier calls, and the layer untestable in isolation.
* A domain model must not hold a domain interface: no property or constructor parameter whose type is a domain interface of the same client or server `domain` layer, bare, nullable, or inside a wrapper such as `Lazy<…>` or `List<…>`
    * **Why:** A domain model is data; a property typed as a domain interface is a dependency that is supplied at the model's construction site rather than injected, so Koin's startup validation does not see it and the class that consumes the model has a dependency its constructor does not declare.
    * **Note:** A property type is resolved through its file's imports and matched against the server's classified domain interfaces by fully-qualified name.
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
    * **Note:** The data layer's counterpart is a [configuration](serverdata.md#configuration): a `[Name]Config` data class beside the Repository or adapter it configures.

##### Examples

A domain model that holds domain interfaces — a violation:

```kotlin
// feature/shop/server/domain/CheckoutInputs.kt
package feature.shop.server.domain

data class CheckoutInputs(
    val getShippingOptions: GetShippingOptions,
    val calculateTotal: Lazy<CalculateTotal>,
)
```

The corrected form when the consumer needs the capabilities: it injects each interface directly.

```kotlin
// feature/shop/server/domain/CheckoutUseCase.kt
package feature.shop.server.domain

class CheckoutUseCase(
    private val getShippingOptions: GetShippingOptions,
    private val calculateTotal: CalculateTotal,
) { ... }
```

The corrected form when the consumer needs the values: one domain interface returns a model carrying them, and the consumer injects that one interface.

```kotlin
// feature/shop/server/domain/CheckoutInputs.kt
package feature.shop.server.domain

data class CheckoutInputs(
    val shippingOptions: List<ShippingOption>,
    val total: Money,
)

fun interface GetCheckoutInputs {
    suspend operator fun invoke(cartId: CartId): CheckoutInputs
}
```

---

## [Extension Function](../src/main/kotlin/architecture/rules/serverdomain/ExtensionFunction.kt)

A top-level extension function in `server.domain` that adds derived behaviour to a
[domain model](#domain-model) or a [shared domain model](feature.md#shared-domain-model). Pure
over its inputs — it computes from the receiver's values and touches nothing else. The
counterpart, one level deeper, of a
[shared extension function](feature.md#shared-extension-function): same shape, server-private
receiver.

* **Note:** The explicit receiver is what makes it an extension of the layer's vocabulary rather
  than free-standing behaviour. A top-level function with no receiver is logic, and logic here is
  a [domain interface](#domain-interface) with a [UseCase](#use-case) behind it.
* **Note:** Convenience logic for a domain interface belongs as a default member function on the
  interface, where it stays co-located with the contract it simplifies.
* **Note:** A helper that only one UseCase needs stays `private` inside that UseCase's file, per
  `ServerDomain.UseCase.breakDownComplexUseCases`. This construct is for an extension the layer
  shares.

##### Requirements

* An Extension Function resides in `feature..server.domain..`
* An Extension Function declares an explicit extension receiver
* An Extension Function has receiver/return/parameter types that are domain models, primitives, or collections of those

---

## [Extension Property](../src/main/kotlin/architecture/rules/serverdomain/ExtensionProperty.kt)

A top-level extension property in `server.domain` that exposes derived state on a
[domain model](#domain-model) or a [shared domain model](feature.md#shared-domain-model).

* **Note:** The same constraints as an [extension function](#extension-function) apply, including
  the explicit receiver: a top-level property with no receiver is state or configuration, not
  vocabulary. Prefer a property when the value is a pure projection of the receiver and is cheap
  to compute on every read.

##### Requirements

* An Extension Property resides in `feature..server.domain..`
* An Extension Property declares an explicit extension receiver
* An Extension Property has a receiver/type that is a domain model, primitive, or collection of those

---

## [Constants](../src/main/kotlin/architecture/rules/serverdomain/Constants.kt)

An `object` in `server.domain` whose only members are `val` constants: the caps, thresholds and
named tags the server's logic agrees on — a retry budget, a batch-size ceiling. The
server-private counterpart of [shared constants](feature.md#shared-constants) — a value both
the client and server have to agree on belongs in the feature root instead.

* **Note:** Anything with behaviour is not a constants object. A pure computation over a model
  belongs on it as an [extension function](#extension-function), and anything that composes
  contracts is a [UseCase](#use-case).

##### Requirements

* A Constants resides in `feature..server.domain..`
* A Constants is an `object` with only `val` properties and no functions
* A Constants does not satisfy: is named `[Name]Workflow`

---

## [Workflow](../src/main/kotlin/architecture/rules/serverdomain/Workflow.kt)

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

* A Workflow resides in `feature..server.domain..`
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

## [Workflow Step](../src/main/kotlin/architecture/rules/serverdomain/WorkflowStep.kt)

A top-level class implementing a [Workflow](#workflow)'s nested `Step` contract — one unit of a
declared process, in its own file.

A step is an adapter, and a thin one. It reads its inputs from the workflow's context, calls the
[domain interfaces](#domain-interface) that do the real work, and writes its outputs back. What
makes it a step rather than a [UseCase](#use-case) is that it **declares** its inputs and
outputs instead of being called in a fixed position: the workflow reads those declarations and
decides when it runs.

Steps live at the top level, not nested in the workflow object, precisely so the catalog
governs them. The workflow holds the definition; the steps are the behaviour.

* **Note:** Name a step for what it does — `[Verb]Step`, as in `ImportStep` or
  `PersistEventsStep`. The suffix is what a reader scans for; the verb is what they read.
* **Note:** A step that needs another step's output declares the artifact, never the step. That
  is the whole mechanism — declaring the dependency is what lets the workflow order the two, and
  naming the sibling directly is how a workflow decays back into a call sequence.

##### Requirements

* A Workflow Step resides in `feature..server.domain..`
* A Workflow Step is a class
* A Workflow Step implements a `[Name]Workflow`'s nested `Step` contract

##### Rules

* A WorkflowStep must not inject another step
    * **Why:** A step that holds another step calls it directly, which puts the order back in the code and takes it away from the declarations. Dependencies between steps are expressed as artifacts the workflow resolves.
* Only a Workflow's composing UseCase may take steps as dependencies
    * **Why:** A step is meaningful only in the order its workflow derives. A ServiceImpl or an unrelated UseCase that injects one calls it out of that order, in a position nothing declared and the workflow cannot see — which is how half a process ends up running somewhere else.
    * **Note:** The composer is the `[Interface]Impl` UseCase that injects the steps, asks the workflow to order them, and runs the plan — so the exemption is an `Impl` in the side's `domain` layer, where UseCases live. A ServiceImpl or any other outer-layer class holding a step is reported.

##### Guidance

* A WorkflowStep should be named for the work it does, as `[Verb]Step`

---

## [Domain Exception](../src/main/kotlin/architecture/rules/serverdomain/DomainException.kt)

A class named `[Name]Exception` representing a failure mode the server names and handles on
its own — an upstream provider refusing a request, a decode that cannot be recovered.

The counterpart of a [shared exception](feature.md#shared-exception), which is the same idea one
level up: a failure both the client and server name, thrown by a server implementation and
matched by client code, living in the feature root because it crosses the wire. A domain
exception does not cross anything. Nothing outside the server can observe it, so it refactors
freely.

`SharedException` already draws the line: an exception that is not wire-visible belongs in
`client.domain` or `server.domain`, not in the root. This is that home.

* **Note:** A failure a [domain interface](#domain-interface) documents belongs in its `@Throws`,
  whichever of the two kinds it is.

##### Requirements

* A Domain Exception resides in `feature..server.domain..`
* A Domain Exception is named `[Name]Exception`
* A Domain Exception is a class extending RuntimeException/Exception
