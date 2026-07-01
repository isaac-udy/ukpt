# Project-wide code rules

These rules are not tied to a construct or a single package — they apply across every feature module. The last four govern the process for [architecture exceptions](exceptions.md); the mechanism itself is documented there.

Context for the exception-handling rules: exceptions defined in the [services contract](services.md#services-the-cross-the-wire-contract) cross the client/server wire as serialised payloads, and the deserialised types don't always extend `Exception`. `AsyncState` is the async-result wrapper that [ViewModels](ui.md#viewmodels) consume.

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
