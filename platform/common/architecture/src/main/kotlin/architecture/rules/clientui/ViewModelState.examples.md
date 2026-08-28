**Bad:** A State with hand-rolled async lifecycle fields — a `Boolean` progress flag paired with an error property. This reimplements the state machine `AsyncState` already provides.

```kotlin
// feature.shop.client.ui.CheckoutState.kt — violates noManualAsyncLifecycleFields
data class CheckoutState(
    val isSaving: Boolean = false,
    val saveError: String? = null,
)
```

**Good:** The same State using `AsyncState<Unit>` for the save action.

```kotlin
// feature.shop.client.ui.CheckoutState.kt
data class CheckoutState(
    val saveAction: AsyncState<Unit> = AsyncState.Idle(),
)
```

---

**Avoid:** Calculated properties that flatten an `AsyncState` back into nullable/default values. The proxy hides the async lifecycle and lets the Screen render a plausible frame before the data exists.

```kotlin
// feature.shop.client.ui.AccountState.kt — flattened async proxies
data class AccountState(
    val account: AsyncState<Account> = AsyncState.Idle(),
) {
    val loadedAccount: Account? get() = account.getOrNull()
    val title: String get() = loadedAccount?.title.orEmpty()
    val messages: List<Message> get() = loadedAccount?.messages.orEmpty()
}
```

**Prefer:** The loaded branch of the Screen receives the non-null domain object directly from the `AsyncState.Success` branch. Calculated properties remain correct when they combine multiple properties, apply a real decision, or derive an affordance.

```kotlin
// feature.shop.client.ui.AccountState.kt
data class AccountState(
    val account: AsyncState<Account> = AsyncState.Idle(),
    val permissions: AsyncState<Permissions> = AsyncState.Idle(),
    val actions: ActionState = ActionState(),
) {
    val canSubmit: Boolean
        get() = account.getOrNull() != null &&
            permissions.getOrNull()?.canWrite == true &&
            !actions.submit.isLoading()
}
```

---

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

**Good:** The dialog is its own destination with its own ViewModel, following the same screen conventions. A confirmation dialog uses plain `NavigationKey`: `complete()` fires the opener's result channel and closes, while `requestClose()` routes through any registered `onCloseRequested` callbacks — the same path as the system back button — and by default just closes. Use `NavigationKey.WithResult<R>` when the dialog returns data.

```kotlin
// feature.items.client.ui.ConfirmDeleteDestination.kt
@Serializable
@SerialName("NavigationKey.ConfirmDeleteDestination")
data class ConfirmDeleteDestination(
    val itemName: String,
) : NavigationKey

// feature.items.client.ui.ConfirmDeleteViewModel.kt
class ConfirmDeleteViewModel : ViewModel() {
    private val navigation by navigationHandle<ConfirmDeleteDestination>()
    val itemName: String get() = navigation.key.itemName
    fun onConfirm() { navigation.complete() }
    fun onDismiss() { navigation.requestClose() }
}

// feature.items.client.ui.ConfirmDeleteDialogScreen.kt — dialog destination (directOverlay present)
@NavigationDestination(ConfirmDeleteDestination::class)
val confirmDeleteDialogScreen = navigationDestination<ConfirmDeleteDestination>(
    metadata = { directOverlayWithFade() }
) {
    val viewModel: ConfirmDeleteViewModel = viewModel()
    AlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = { Text("Delete ${viewModel.itemName}?") },
        confirmButton = { TextButton(onClick = viewModel::onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = viewModel::onDismiss) { Text("Cancel") } },
    )
}

// feature.items.client.ui.ItemListViewModel.kt — opener consumes the outcome
private val deleteResult by registerForNavigationResult(
    onCompleted = { loadItems() },
)
fun onDeleteRequested(item: Item) {
    deleteResult.open(ConfirmDeleteDestination(itemName = item.name))
}
```
