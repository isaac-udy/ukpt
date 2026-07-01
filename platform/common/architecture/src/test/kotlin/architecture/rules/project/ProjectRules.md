# Project-wide code rules

These are layer-level `ProjectRules` — they are not tied to a construct or a single package; they apply across every feature module. The same group also holds four rules governing the architecture-exception process itself (human sign-off, KDoc, temporariness); those are documented with the exception mechanism in [architecture exceptions](exceptions.md).

## Exception handling

Two of these rules exist because of the urpc transport: exceptions defined in the [services contract](services.md#services-the-cross-the-wire-contract) cross the client/server wire as serialised payloads, and the deserialised types don't always extend `Exception`. The third closes the manual-`try/catch` escape hatch on the client — `AsyncState` is the async-result wrapper that [ViewModels](ui.md#viewmodels) consume.

{{rule:ProjectRules.noCatchException}}
{{rule:ProjectRules.serviceExceptionsSerializable}}
{{rule:ProjectRules.noDirectAsyncStateConstruction}}

## Imports

{{rule:ProjectRules.noWildcardImports}}

## Action and request types

{{rule:ProjectRules.sealedActionVariants}}
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
