`feature.ukpt`'s screen is a function-based `@NavigationDestination`: the entry `UkptScreen` injects its ViewModel with `viewModel()` and delegates to a stateless `internal UkptScreenContent` that takes state plus callbacks (the `screenContentCompanion` and `viewModelInjection` rules):

```kotlin
// UkptScreen (in :client) — feature.ukpt.ui
@Composable
@NavigationDestination(UkptDestination::class)
fun UkptScreen(
    viewModel: UkptViewModel = viewModel(),   // viewModel(), not koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    UkptScreenContent(state = state, onGreet = viewModel::onGreetClicked)
}

// Stateless content: renders state, reports intent via callbacks, so it renders without a
// ViewModel and is snapshot-testable from a @Preview.
@Composable
internal fun UkptScreenContent(state: UkptState, onGreet: () -> Unit) { /* … */ }
```

A dialog/overlay screen, illustrated (the base template ships only the full-screen `UkptScreen`): the Destination lives in `:api`, and the property-based `navigationDestination` in `:client` declares `directOverlay()` metadata and resolves its ViewModel via `viewModel()` inside the block.

```kotlin
// Destination (in :api)
@Serializable
data class ChangeRoleDestination(
    val memberName: String,
    val currentRole: UserRole,
) : NavigationKey.WithResult<UserRole>

// Screen (in :client) — property-based with directOverlay metadata
@NavigationDestination(ChangeRoleDestination::class)
val changeRoleScreen = navigationDestination<ChangeRoleDestination>(
    metadata = { directOverlay() }
) {
    val viewModel: ChangeRoleViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    ChangeRoleDialog(
        memberName = state.memberName,
        selectedRole = state.selectedRole,
        onRoleSelected = viewModel::onRoleSelected,
        onConfirm = viewModel::onConfirm,
        onDismiss = viewModel::onDismiss,
    )
}
```
