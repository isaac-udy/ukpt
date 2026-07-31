> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/clientdomain/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Client Domain](../src/main/kotlin/architecture/rules/clientdomain/ClientDomain.kt)

`feature.[name].client.domain` — the client's private logic. Single-function
[domain interfaces](clientdomain.md#domain-interface) that [ViewModels](clientui.md#view-model) consume and
[Repositories](clientdata.md#repository) provide, [UseCases](#use-case) composing several of them, and
[domain models](#domain-model) that never leave the client. Around those,
[extension functions](#extension-function) and [extension properties](#extension-property) add
derived behaviour to a model, and a [constants](#constants) object holds the values this side's
logic agrees on.

It is **pure**: it may import feature roots and nothing else. No Compose, no Ktor, no
persistence, no service contracts. That purity is what makes it testable without a harness, and
what stops client abstractions leaking into the wire vocabulary.

A domain interface may be **published to `:api`** when another feature's UI needs it; use cases
and domain models stay in `:client`. Publishing is moving the file, not changing the package.

This layer is the exact mirror of [`server.domain`](serverdomain.md) — same construct names,
same rules, opposite side. The layer supplies the context, so the names never repeat it.

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
    * **Note:** A generated Exposed table (`platform.server.postgres.tables.**`) counts as a platform dependency — naming one is naming a column, whatever the package reads as.
* The `client.domain` layer may depend on another feature's root, but only via that feature's `:api` module
    * **Enforced by:** `ModuleRules.clientApiOnly`, `ModuleRules.crossFeatureCodeViaApi`
* A `client.domain` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Note:** A subsystem package is a capability of the feature that nothing outside it names, and it is never published (`ModuleRules.subsystemsNotPublished`): the constructs classify inside one exactly as they do at the layer root, because a subsystem is a location rather than a kind of thing.
    * **Note:** Composition across two subsystems belongs to their shared ancestor, where a shared payload is an ordinary [domain model](#domain-model) and a shared contract an ordinary [domain interface](#domain-interface).
    * **Enforced by:** `ProjectRules.subsystemVisibility`

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

* A Domain Interface's primary-function parameters must be shared domain models, side-private domain models, nested types, primitives, standard date/time value types, collections of those, or a `Flow` of those
* A Domain Interface's primary-function return type must be shared domain models, side-private domain models, nested types, primitives, standard date/time value types, collections of those, a `Flow` of those, or no value
* A Domain Interface's functions must propagate errors via thrown exceptions, never via the return type
    * **Why:** A result type that carries the failure makes every caller unwrap it, and the layer's vocabulary grows a wrapper around each contract. Thrown exceptions keep the primary function's return type the thing it produces.
    * **Note:** Known exceptions should be their own type extending RuntimeException, marked with `@Throws`.
    * **Note:** `@Throws` on a `suspend` function must include `kotlin.coroutines.cancellation.CancellationException` (or a superclass such as `Exception`): an interface published to `:api` compiles for every target, and kotlinc rejects the function on iOS without it.
* A Domain Interface must be implemented by a Repository (as a property) or by a UseCase
    * **Note:** The test accepts either a class whose parents include the interface (a UseCase) or a `[Name]Repository` with a property that references the interface.

##### Guidance

* A Domain Interface may define additional default functions that call the primary function
* When several mutations act on one aggregate and share a return shape, prefer a single `Update[Noun]` interface over one interface per mutation: a nested `sealed interface Update` carries the variants, the abstract `invoke(id, update)` is the single entry point, and default functions (`title(...)`, `addMember(...)`) keep call sites flat. Reads stay separate — their return types differ by nature. An interface published through `:api` stays single-purpose: publish exactly the capability being shared, never a whole mutation family.

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

## [Use Case](../src/main/kotlin/architecture/rules/clientdomain/UseCase.kt)

A class that implements a single [domain interface](#domain-interface).

* **Note:** Immutable helper properties, such as loggers, are permitted. "No mutable state"
  forbids `var` properties, not properties in general.
* **Note:** If a UseCase only injects a single other domain interface, consider whether that
  logic should become a default function of the other domain interface instead.
* **Note:** When breaking down a complex UseCase, use file-private extension functions,
  private functions, or nested classes instead of additional domain interfaces or UseCases.

##### Requirements

* An Use Case resides in `feature..client.domain..`
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

## [Domain Model](../src/main/kotlin/architecture/rules/clientdomain/DomainModel.kt)

A model that belongs to one side only: a draft being edited, a cursor, an in-flight state
machine, a computed projection, a payload written to a column.

The contrast with a [shared domain model](feature.md#shared-domain-model) is what the package
split encodes, and it is **residence and reach** rather than shape. A shared domain model is part
of the feature's shared language, named by both sides and readable by other features, so renaming
a field is a compatibility event with cross-feature blast radius. A domain model is private to
its side: nothing outside can observe a change, so it refactors freely.

Serialization does not decide which of the two a type is. A domain model may carry
`@Serializable` — a payload persisted in a column, state restored across a process death — and
what that costs is a migration for its own stored data, never a cross-feature compatibility
event.

The **network** is what decides: a model the other side receives has stopped being side-private,
and belongs in the feature root with the blast radius that comes with it.

##### Requirements

* A Domain Model resides in `feature..client.domain..`
* A Domain Model is a class or interface
* A Domain Model satisfies one of: {is `sealed`, is a `data class`, is an `enum class`, is a `value class`}

##### Rules

* A domain model must be immutable — no `var` properties
    * **Why:** Shared mutable state in the middle of the hexagon makes call order load-bearing and defeats the layer's testability.
* A domain model that needs to cross the network belongs in the feature root instead
    * **Note:** Crossing the network is the test, not carrying `@Serializable`: a payload a StorageClass writes into a column, or a state a client restores after a process death, is serialized and still side-private.
    * **Note:** Persistence is `server.data`'s concern: a model that is stored but not shared is mapped to a [storage record](serverdata.md#storage-record) there, not promoted to the root.
    * **Verification:** not automatically verifiable; enforced by review.
* A domain model must not re-implement a concept a shared domain model already defines; use or compose the shared model instead
    * **Note:** The feature's language has one source of truth in the root; a side-private copy of a concept drifts from it as both change.
    * **Verification:** not automatically verifiable; enforced by review.

---

## [Extension Function](../src/main/kotlin/architecture/rules/clientdomain/ExtensionFunction.kt)

A top-level extension function in `client.domain` that adds derived behaviour to a
[domain model](#domain-model) or a [shared domain model](feature.md#shared-domain-model). Pure
over its inputs — it computes from the receiver's values and touches nothing else. The mirror of
a [shared extension function](feature.md#shared-extension-function) one level down: same shape,
side-private receiver.

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
named tags this side's logic agrees on. The side-private counterpart of
[shared constants](feature.md#shared-constants) — a value both sides have to agree on belongs in
the feature root instead, because agreement is what makes it shared.

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
* A Workflow Step implements a workflow's nested `Step` contract

##### Rules

* A WorkflowStep must not inject another step
    * **Why:** A step that holds another step calls it directly, which puts the order back in the code and takes it away from the declarations. Dependencies between steps are expressed as artifacts the workflow resolves.
* Only a Workflow's composing UseCase may take steps as dependencies
    * **Why:** A step is meaningful only in the order its workflow derives. A ViewModel or an unrelated UseCase that injects one calls it out of that order, in a position nothing declared and the workflow cannot see — which is how half a process ends up running somewhere else.
    * **Note:** The composer is the `[Interface]Impl` UseCase that injects the steps, asks the workflow to order them, and runs the plan.

##### Guidance

* A WorkflowStep should be named for the work it does, as `[Verb]Step`

---

## [Domain Exception](../src/main/kotlin/architecture/rules/clientdomain/DomainException.kt)

A class named `[Name]Exception` representing a failure mode this side names and handles on its
own — a cache that cannot be read, a draft that will not restore.

The counterpart of a [shared exception](feature.md#shared-exception), which is the same idea one
level up: a failure both sides name, thrown by a server implementation and matched by client
code, living in the feature root because it crosses the wire. A domain exception does not cross
anything. Nothing outside this side can observe it, so it refactors as freely as any other
side-private declaration.

`SharedException` already draws the line this construct sits on the other side of — *"an
exception that is not wire-visible is side-private: it belongs in that side's `domain`, not in
the root."* This is that home.

* **Note:** A failure a [domain interface](#domain-interface) documents belongs in its `@Throws`,
  whichever of the two kinds it is.

##### Requirements

* A Domain Exception resides in `feature..client.domain..`
* A Domain Exception is named `[Name]Exception`
* A Domain Exception is a class extending RuntimeException/Exception
