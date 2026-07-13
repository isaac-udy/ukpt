> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/project/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Project Rules](../src/main/kotlin/architecture/rules/project/ProjectRules.kt)

These rules are not tied to a Construct or a single package; they apply across every feature
module. Several govern the process for [architecture exceptions](exceptions.md); the mechanism
itself is documented there.

Context for the exception-handling rules: exceptions defined in the
[services contract](services.md#service-interface) cross the client/server boundary as
serialised payloads, and the deserialised types don't always extend `Exception`. `AsyncState`
is the async-result wrapper that [ViewModels](ui.md#view-model) consume.

##### Rules

* A `try/catch` block must never catch `Exception`; use `catch (t: Throwable)` or a specific exception type instead
    * **Why:** The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into types that may not extend `Exception`, such as kotlinx-serialization error types. A `catch (Exception)` block silently misses these, so the error propagates uncaught and crashes on an internal thread instead of being handled by application code.
    * **Note:** On the client, prefer `AsyncState.fromSuspending` over manual `try/catch`: it captures exceptions correctly and integrates with the ViewModel state pattern.
    * **Note:** Catching a specific exception type, such as `catch (t: IllegalArgumentException)`, is always acceptable when you only want to handle that case.
* An exception type defined in `services` (the client/server contract) must be annotated with `@Serializable`
    * **Why:** The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into typed payloads on the client; without `@Serializable` the type and message are lost in transit and the client receives a generic deserialisation failure. Exceptions inside `services.internal.*` stay server-side and never reach the client, so they are out of scope.
    * **Note:** Prefer subclassing `PresentableException` with a deliberate `retryable` flag: streaming flows auto-retry retryable errors and surface terminal ones, and the unary error UI offers a Retry action only when `retryable`.
* An import must not use a wildcard; always list the explicit symbols
    * **Why:** Wildcards hide which symbols a file depends on, break several architecture tests (which inspect import names directly), and silently pull in new names when the imported package adds members.
* An `AsyncState` must never be constructed directly via `Loading`/`Success`/`Error`; use `AsyncState.fromSuspending`/`fromFlow` instead
    * **Why:** Direct construction skips the exception capture, cancellation, and state-flow protocol that `AsyncState.fromSuspending`/`fromFlow` handle uniformly, silently breaking the contract the rest of the codebase relies on. Files that legitimately build AsyncState values (defining its semantics, or the server-side status pattern) opt out with `@file:ArchitectureException`.
    * **Note:** A construction inside a `@Preview` function is sample state for a snapshot/preview, not production wiring, so it is exempt — no `@ArchitectureException` is needed. The rule still flags direct construction in any real code, including a `@Preview`'s non-preview helpers.
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
