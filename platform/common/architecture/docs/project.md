> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/project/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Project Rules](../src/main/kotlin/architecture/rules/project/ProjectRules.kt)

These rules are not tied to a Construct or a single package; they apply across every feature
module. Several govern the process for [architecture exceptions](exceptions.md); the mechanism
itself is documented there.

Context for the exception-handling rules: exceptions defined in the
[services contract](serverservices.md#service-interface) cross the client/server boundary as
serialised payloads, and the deserialised types don't always extend `Exception`. `AsyncState`
is the async-result wrapper that [ViewModels](clientui.md#view-model) consume.

##### Rules

* A `try/catch` block must never catch `Exception`; use `catch (t: Throwable)` or a specific exception type instead
    * **Why:** The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into types that may not extend `Exception`, such as kotlinx-serialization error types. A `catch (Exception)` block silently misses these, so the error propagates uncaught and crashes on an internal thread instead of being handled by application code.
    * **Note:** On the client, prefer `AsyncState.fromSuspending` over manual `try/catch`: it captures exceptions correctly and integrates with the ViewModel state pattern.
    * **Note:** Catching a specific exception type, such as `catch (t: IllegalArgumentException)`, is always acceptable when you only want to handle that case.
* An exception type defined in `server.services` (the client/server contract) must be annotated with `@Serializable`
    * **Why:** The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into typed payloads on the client; without `@Serializable` the type and message are lost in transit and the client receives a generic deserialisation failure. Exceptions under a `server.services` sub-package stay server-side and never reach the client, so they are out of scope.
    * **Note:** Prefer subclassing `PresentableException` with a deliberate `retryable` flag: streaming flows auto-retry retryable errors and surface terminal ones, and the unary error UI offers a Retry action only when `retryable`.
* An import must not use a wildcard; always list the explicit symbols
    * **Why:** Wildcards hide which symbols a file depends on, break several architecture tests (which inspect import names directly), and silently pull in new names when the imported package adds members.
* An `AsyncState` must never be constructed directly via `Loading`/`Success`/`Error`; use `AsyncState.fromSuspending`/`fromFlow` instead
    * **Why:** Direct construction skips the exception capture, cancellation, and state-flow protocol that `AsyncState.fromSuspending`/`fromFlow` handle uniformly, silently breaking the contract the rest of the codebase relies on. Files that legitimately build AsyncState values (defining its semantics, or the server-side status pattern) opt out with `@file:ArchitectureException`.
    * **Note:** A construction inside a `@Preview` function is sample state for a snapshot/preview, not production wiring, so it is exempt — no `@ArchitectureException` is needed. The rule still flags direct construction in any real code, including a `@Preview`'s non-preview helpers.
* A package in a feature layer must name that layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Why:** A subsystem package is a boundary, not a namespace. One level inward is what gives it an interior: the parent names the subsystem, and the subsystem chooses what of itself the parent may see — the same property depth-is-privacy gives the whole taxonomy. Unlimited inward visibility would make a subtree a prefix and nothing more, so the root could name a vendor client three levels down and no boundary would exist anywhere.  Sideways is forbidden because two subsystems that name each other are one subsystem with a package split through it. Composition between them belongs to their shared ancestor, which is the package that is allowed to know both.
    * **Note:** Upward is unrestricted: a shared payload is an ordinary domain model at the shared ancestor and a shared contract an ordinary domain interface there, and the layer's own purity rules already bound what either can do.
    * **Note:** `server.data.storage` and `client.data.storage` are visible layer-wide within their own feature's data layer. Storage is not a subsystem — it is the Row-speaking half of the layer, and one flat persistence surface is what gives a table a single owner.
    * **Note:** Tested over imports and over fully-qualified references in the file body, because a type named in a type position has no import to inspect. A name that resolves to no project declaration — generated code, a library — is not tested.
    * **Note:** Keyed on the package alone. A declaration's visibility modifier says nothing about which package may name it, so `internal` and `public` neighbours are governed identically.
* A subsystem package outside the domain layer must name the domain layer only through the matching domain subsystem package (same feature, same client or server implementation, same subsystem path), that package's direct children, and their ancestors
    * **Why:** A subsystem in an outer layer — `feature.shop.client.ui.checkout`, `feature.shop.client.data.checkout` — may name `feature.shop.client.domain.checkout`, its direct children, and its ancestors, and nothing else in the domain layer. This keeps a subsystem's implementations next to the domain contracts they satisfy: the package that implements `feature.shop.client.domain.checkout` cannot also implement `feature.shop.client.domain.payments`. The matching package's depth follows from the contracts it satisfies rather than being chosen.
    * **Note:** A layer-root file is unconstrained by the mirror: a root Repository provides root-declared contracts, as it always has. The rule binds only a file that is itself in a subsystem package.
    * **Note:** An outer subsystem with no domain twin is legal and needs no special case — the rule restricts domain imports, and a package with none has nothing to restrict.
* An action/request type must model its variants as a `sealed interface`/`sealed class` (each variant a `data class`), not as a single type with an `enum` discriminator and nullable fields
    * **Why:** A sealed hierarchy makes illegal field combinations unrepresentable and lets `when` exhaustiveness drive handling, so adding a variant surfaces every site that must handle it.
    * **Note:** "An enum that should be a sealed class" can't be detected reliably by the tests.
    * **Verification:** not automatically verifiable; enforced by review.
* An architecture exception may only be added after discussing the exception with a human author
    * **Verification:** not automatically verifiable; enforced by review.
* An architecture exception is not a valid way to resolve an immediate architecture-test failure; fix the code or the rule first
    * **Verification:** not automatically verifiable; enforced by review.
* An architecture exception must explain why it exists and the intended resolution in a non-blank `reason` argument on the `@ArchitectureException`
    * **Note:** The test covers `@ArchitectureException` on declarations, including file-level `@file:` annotations; `// architecture-exception:` comments in build files carry their reason inline and are out of scope.
    * **Note:** The explanation must be the annotation's own `reason` argument — it is machine-readable, travels with the annotation, and is the natural form for a file-level `@file:ArchitectureException(reason = …)`. A KDoc comment alone does not satisfy this rule.
* Every `@Serializable` type that participates in polymorphic serialization must pin an explicit `@SerialName`
    * **Why:** Without a `@SerialName`, kotlinx derives the discriminator from the fully-qualified class name — so the package path silently becomes part of the serialized format, and the first package move invalidates every stored row and every persisted client state that carries one. Pinning makes the wire value an explicit, reviewable decision.  Sealed variants are the obvious case; a top-level `@Serializable` class registered for polymorphic dispatch — an Enro `NavigationKey` is exactly this — is *not* a sealed variant and slips past a rule that only checks those.
    * **Note:** Checked per declaration, not per file: an annotation on a nested type does not pin its parent.
    * **Note:** A derived discriminator fails *silently* wherever the decoder is tolerant: a decoder that falls back on unknown types persists the fallback on its next write.
* A `@SerialName` on a polymorphically serialized type must encode the type that encloses it: exactly `NavigationKey.<Name>` for a navigation destination, and a value ending with the type-nesting chain from the outermost declaring type for a sealed variant
    * **Why:** A discriminator is read far from the class that produced it — in a stored JSONB row, a captured request, a browser history entry — and a bare word is unreadable there. Three different destinations declare a sealed `Action` with a `Delete` variant, so `"Delete"` names four things and identifies none of them; `"EventCardOptionsDestination.Action.Delete"` identifies exactly one. Encoding the enclosing type is what makes a payload self-describing to whoever is holding it.  The value stays **package-free**, which is the other half of the requirement. A package path in a discriminator is what couples the wire format to where the class lives and makes moving it a migration; a type-nesting chain moves with the class, so repackaging stays free.
    * **Note:** Only the required suffix is checked for a sealed variant, so a hierarchy pinned to a pre-move fully-qualified name for compatibility already satisfies this — the type chain is the end of an FQN.
    * **Note:** The required chain runs from the outermost declaring type, not just the immediate sealed parent: two destinations each nesting a sealed `Action` with a `Delete` variant would otherwise share the discriminator `"Action.Delete"`, and a value two readers can claim identifies neither.
    * **Note:** A destination is checked exactly, not by suffix: nothing durable rides on a navigation key, so there is no compatibility case that would justify a longer value.
* A `TransactionRunner` may only be injected by a UseCase or a Repository
    * **Why:** Opening a transaction is a statement about which writes have to land together, and only two places are positioned to make it. A [UseCase](serverdomain.md#use-case) composes several domain interfaces and is the one place that knows the whole unit of work; a [Repository](serverdata.md#repository) owns the writes it makes through its StorageClasses. Everything else is on the wrong side of that knowledge: an entry point in `server.services` would be scoping a transaction around contracts whose implementations it cannot see, and a [StorageClass](serverdata.md#storage-class) already runs inside whatever transaction its caller opened — taking the runner would let it widen a boundary it is a participant in.
    * **Note:** A block that spans two features' writes is a UseCase by construction: a Repository may not inject a domain interface, so it cannot reach another feature's contract to put inside one.
* Two features must not declare service exceptions with the same simple name
    * **Why:** urpc identifies an error by `throwable::class.simpleName` — `ServiceError.from` sends it and the client matches on it — so the simple name *is* the wire contract. That makes package moves free, which is why the migration is safe, but it also means two features with a same-named exception are indistinguishable to a client.
    * **Note:** Scoped to exceptions that reach the wire: those declared in the services contract package (`feature.x.server.services`, excluding its server-only sub-packages) or in a feature root, which is the vocabulary both sides name. Two server-private exceptions with one name never meet, so they do not collide.

##### Guidance

* An architecture exception should be temporary; revisit it periodically and remove it once the underlying issue is resolved

##### Examples

Example for `ProjectRules.sealedActionVariants`:

```kotlin
// Good
sealed interface UserAction {
    data class Rename(val id: User.Id, val newName: String) : UserAction
    data class Delete(val id: User.Id) : UserAction
}

// Avoid
enum class ActionType { RENAME, DELETE }
data class UserActionRequest(val id: User.Id, val type: ActionType, val newName: String? = null)
```
