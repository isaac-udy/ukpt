<!--
  GENERATED FILE — do not edit.
  Narrative source: src/test/kotlin/architecture/rules/project/ProjectRules.md (structured blocks come from the rule catalog).
  Regenerate: UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test
-->

# Project-wide code rules

These are layer-level `ProjectRules` — they are not tied to a construct or a single package; they apply across every feature module. The same group also holds four rules governing the architecture-exception process itself (human sign-off, KDoc, temporariness); those are documented with the exception mechanism in [architecture exceptions](exceptions.md).

## Exception handling

Two of these rules exist because of the urpc transport: exceptions defined in the [services contract](services.md#services-the-cross-the-wire-contract) cross the client/server wire as serialised payloads, and the deserialised types don't always extend `Exception`. The third closes the manual-`try/catch` escape hatch on the client — `AsyncState` is the async-result wrapper that [ViewModels](ui.md#viewmodels) consume.

* **`ProjectRules.noCatchException`** `✅ tested` — `try/catch` blocks must never catch `Exception` — use `catch (t: Throwable)` or a specific exception type
    * **Why**: The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into types that may not extend `Exception` (e.g. kotlinx-serialization / kRPC error types). A `catch (Exception)` block silently misses these, so the error propagates uncaught and crashes on an internal thread instead of being handled by application code.
    * **Note**: On the client, prefer `AsyncState.fromSuspending` over manual `try/catch` — it captures exceptions correctly and integrates with the ViewModel state pattern.
    * **Note**: Catching a specific exception type (e.g. `catch (t: IllegalArgumentException)`) is always acceptable when you only want to handle that case.
* **`ProjectRules.serviceExceptionsSerializable`** `✅ tested` — Exception types defined in `services` (the cross-the-wire contract) must be annotated with `@Serializable`
    * **Why**: The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into typed payloads on the client; without `@Serializable` the type and message are lost in transit and the client receives a generic deserialisation failure. Exceptions inside `services.internal.*` stay server-side and don't cross the wire, so they are out of scope.
    * **Note**: Prefer subclassing `PresentableException` with a deliberate `retryable` flag — streaming flows auto-retry retryable errors and surface terminal ones; the unary error UI offers a Retry action only when `retryable`.
* **`ProjectRules.noDirectAsyncStateConstruction`** `✅ tested` — `AsyncState.Loading`/`Success`/`Error` must not be constructed directly — use `AsyncState.fromSuspending`/`fromFlow`
    * **Why**: Direct construction skips the exception capture, cancellation, and state-flow protocol that `AsyncState.fromSuspending`/`fromFlow` handle uniformly — silently breaking the contract the rest of the codebase relies on. Files that legitimately build AsyncState values (defining its semantics, or the server-side status pattern) opt out with `@file:ArchitectureException`.

## Imports

* **`ProjectRules.noWildcardImports`** `✅ tested` — Imports must not use wildcards — always list the explicit symbols
    * **Why**: Wildcards hide which symbols a file depends on, break a number of architecture-test checks (which inspect import names directly), and silently pull in new names when the imported package adds members.

## Action and request types

* **`ProjectRules.sealedActionVariants`** `📋 guidance` — Model action/request variants as a `sealed interface`/`sealed class` (each variant a `data class`), not a single type with an `enum` discriminator and nullable fields
* **Why**: A sealed hierarchy makes illegal field combinations unrepresentable and lets `when` exhaustiveness drive handling, so adding a variant surfaces every site that must handle it.
* **Example**:
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
* **Note**: Enforced by review, not a static test — "an enum that should be a sealed class" can't be detected reliably by Konsist.

