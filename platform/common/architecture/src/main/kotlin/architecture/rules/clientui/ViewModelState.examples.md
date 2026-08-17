A State that is a transparent container for domain objects plus calculated properties; display formatting lives with the Screen as a `@Composable` extension property, not in the State.

```kotlin
// feature.user.client.ui.UserDetailState.kt
data class UserDetailState(
    val user: User,
    val isEditing: Boolean,
) {
    // Calculated property for logic
    val canEditName: Boolean get() = user.isVerified && isEditing
}

// feature.user.client.ui.UserDetailScreen.kt
// Extension property for display
val User.displayRole: String
    @Composable
    get() = when(role) {
        User.Role.Admin -> stringResource(Res.string.role_admin)
        User.Role.Member -> stringResource(Res.string.role_member)
    }
```

---

**Bad:** A State with a dialog visibility flag and an inline `AlertDialog` in the Screen — the dialog's lifecycle is coupled to screen state instead of the navigation backstack.

```kotlin
// feature.items.client.ui.ItemListState.kt
data class ItemListState(
    val items: AsyncState<List<Item>> = AsyncState.Unstarted,
    val showDeleteDialog: Boolean = false,  // violates noDialogVisibilityFlags
    val itemToDelete: Item? = null,
)

// feature.items.client.ui.ItemListScreen.kt — inline dialog (violates dialogPrimitivesOnlyInDialogDestinations)
if (state.showDeleteDialog && state.itemToDelete != null) {
    AlertDialog(
        onDismissRequest = viewModel::onDismissDelete,
        title = { Text("Delete item?") },
        confirmButton = { TextButton(onClick = viewModel::onConfirmDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = viewModel::onDismissDelete) { Text("Cancel") } },
    )
}
```

**Good:** The dialog is its own destination with a result type; the opener consumes the result through a navigation result channel and reloads.

```kotlin
// feature.items.client.ui.ConfirmDeleteDestination.kt
@Serializable
@SerialName("NavigationKey.ConfirmDeleteDestination")
data class ConfirmDeleteDestination(
    val itemName: String,
) : NavigationKey.WithResult<Boolean>

// feature.items.client.ui.ConfirmDeleteDialogScreen.kt — dialog destination file (directOverlay present)
@NavigationDestination(ConfirmDeleteDestination::class)
val confirmDeleteDialogScreen = navigationDestination<ConfirmDeleteDestination>(
    metadata = { directOverlayWithFade() }
) {
    val key = navigation.key
    AlertDialog(
        onDismissRequest = { navigation.requestClose() },
        title = { Text("Delete ${key.itemName}?") },
        confirmButton = { TextButton(onClick = { navigation.complete(true) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { navigation.requestClose() }) { Text("Cancel") } },
    )
}

// feature.items.client.ui.ItemListViewModel.kt — opener consumes the result
private val deleteResult by registerForNavigationResult<Boolean> {
    if (it) loadItems()
}
fun onDeleteRequested(item: Item) {
    deleteResult.open(ConfirmDeleteDestination(itemName = item.name))
}
```
