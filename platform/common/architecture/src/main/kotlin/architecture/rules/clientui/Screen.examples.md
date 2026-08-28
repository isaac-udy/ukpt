A Screen rendering required async data exhaustively: the `when` covers every `AsyncState` variant, and minimal navigation chrome (`onBack`) stays available in non-success states.

```kotlin
// feature.shop.client.ui.OrderDetailScreen.kt
@Composable
internal fun OrderDetailScreenContent(
    state: OrderDetailState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    when (val order = state.order) {
        is AsyncState.Idle,
        is AsyncState.Loading -> LoadingContent(onBack = onBack)
        is AsyncState.Error -> ErrorContent(order.error, onRetry = onRetry, onBack = onBack)
        is AsyncState.Success -> OrderContent(order.data, onBack = onBack)
    }
}
```

---

A dialog/overlay screen: the Destination lives in `:client` (published to `:api` only when a second feature navigates to it), and the property-based `navigationDestination` declares `directOverlay()` metadata and resolves its ViewModel via `viewModel()` inside the block.

```kotlin
// Destination (in :client)
@Serializable
@SerialName("NavigationKey.ChangeRoleDestination")
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
