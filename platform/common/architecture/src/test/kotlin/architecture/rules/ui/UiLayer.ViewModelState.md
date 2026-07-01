# ViewModel State

* **Definition**: The complete, immutable representation of a Screen's data at a single point in time.
* **Note**: `AsyncState` covers action progress as well as loads — e.g. a "save" action as `AsyncState<Unit>`. Never directly construct `AsyncState.Loading`/`Success`/`Error` — use `AsyncState.fromSuspending`/`fromFlow`; that prohibition is enforced project-wide by `ProjectRules.noDirectAsyncStateConstruction`.
* **Example**:
```kotlin
// feature.user.ui.UserDetailState.kt
data class UserDetailState(
    val user: User,
    val isEditing: Boolean,
) {
    // Calculated property for logic
    val canEditName: Boolean get() = user.isVerified && isEditing
}

// feature.user.ui.UserDetailScreen.kt
// Extension property for display
val User.displayRole: String
    @Composable
    get() = when(role) {
        User.Role.Admin -> stringResource(Res.string.role_admin)
        User.Role.Member -> stringResource(Res.string.role_member)
    }
```
