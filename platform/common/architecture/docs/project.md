> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Sources: @Describe annotations in the Kotlin catalog in `src/test/kotlin/architecture/rules/project/` (narrative + rules), plus the `*.examples.md` files beside it.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

# [Project Rules](../src/test/kotlin/architecture/rules/project/ProjectRules.kt)

These rules are not tied to a construct or a single package — they apply across every feature
module. The guidance entries govern the process for [architecture exceptions](exceptions.md);
the mechanism itself is documented there.

Context for the exception-handling rules: exceptions defined in the
[services contract](services.md#service-interface) cross the client/server wire as serialised
payloads, and the deserialised types don't always extend `Exception`. `AsyncState` is the
async-result wrapper that [ViewModels](ui.md#view-model) consume.

##### Rules

* A `try/catch` block must never catch `Exception` — use `catch (t: Throwable)` or a specific exception type
    * **Why**: The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into types that may not extend `Exception` (e.g. kotlinx-serialization / kRPC error types). A `catch (Exception)` block silently misses these, so the error propagates uncaught and crashes on an internal thread instead of being handled by application code.
    * **Note**: On the client, prefer `AsyncState.fromSuspending` over manual `try/catch` — it captures exceptions correctly and integrates with the ViewModel state pattern.
    * **Note**: Catching a specific exception type (e.g. `catch (t: IllegalArgumentException)`) is always acceptable when you only want to handle that case.
* An exception type defined in `services` (the cross-the-wire contract) must be annotated with `@Serializable`
    * **Why**: The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into typed payloads on the client; without `@Serializable` the type and message are lost in transit and the client receives a generic deserialisation failure. Exceptions inside `services.internal.*` stay server-side and don't cross the wire, so they are out of scope.
    * **Note**: Prefer subclassing `PresentableException` with a deliberate `retryable` flag — streaming flows auto-retry retryable errors and surface terminal ones; the unary error UI offers a Retry action only when `retryable`.
* An import must not use a wildcard — always list the explicit symbols
    * **Why**: Wildcards hide which symbols a file depends on, break a number of architecture-test checks (which inspect import names directly), and silently pull in new names when the imported package adds members.
* An `AsyncState` must never be constructed directly via `Loading`/`Success`/`Error` — use `AsyncState.fromSuspending`/`fromFlow`
    * **Why**: Direct construction skips the exception capture, cancellation, and state-flow protocol that `AsyncState.fromSuspending`/`fromFlow` handle uniformly — silently breaking the contract the rest of the codebase relies on. Files that legitimately build AsyncState values (defining its semantics, or the server-side status pattern) opt out with `@file:ArchitectureException`.
* An architecture exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and the intended resolution
    * **Note**: Checked for `@ArchitectureException` on declarations; `// architecture-exception:` comments in build files carry their reason inline and are out of scope.

##### Guidance

* An action/request type must model its variants as a `sealed interface`/`sealed class` (each variant a `data class`), not as a single type with an `enum` discriminator and nullable fields
    * **Why**: A sealed hierarchy makes illegal field combinations unrepresentable and lets `when` exhaustiveness drive handling, so adding a variant surfaces every site that must handle it.
    * **Note**: Enforced by review, not a static test — "an enum that should be a sealed class" can't be detected reliably by Konsist.
* An architecture exception may only be added after discussing the exception with a human author
* An architecture exception is not a valid way to resolve an immediate architecture-test failure without user feedback — fix the code or the rule first
* An architecture exception is temporary — revisit it periodically and remove it once the underlying issue is resolved

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
