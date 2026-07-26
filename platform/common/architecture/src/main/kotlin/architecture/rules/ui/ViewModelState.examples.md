`feature.ukpt`'s State is a plain, immutable data-class container; the ViewModel replaces it wholesale with `state.update { copy(...) }`:

```kotlin
// feature.ukpt.ui.UkptState
data class UkptState(
    val message: String = "Hello, ukpt!",
    val greetings: Int = 0,
)
```

When state carries domain objects, add calculated properties for logic — but keep display formatting out of the State and put it with the Screen as a `@Composable` extension property. Illustrated (the base template's `UkptState` is too simple to need either):

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
