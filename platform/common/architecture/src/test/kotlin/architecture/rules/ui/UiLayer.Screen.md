# Screens

A Composable function (or property-based `navigationDestination`) that defines the layout and visual representation of a feature or portion of a feature.

### Dialog / Overlay Screens

A Screen that is presented as a dialog or overlay on top of the current screen, rather than pushing onto the navigation backstack — governed by the `UiLayer.Screen.overlayViaDsl` and `UiLayer.Screen.overlayViewModel` rules below. Regular screens that push to the backstack should use the standard `@Composable fun` pattern; the property-based `navigationDestination` DSL is specifically for screens that need to declare custom metadata (such as `directOverlay()`). The property name may end in `Screen` or `Destination` — both are accepted because the property *is* the destination declaration site.

* **Example**:
```kotlin
// Destination (in :api)
@Serializable
data class ChangeRoleDestination(
    val memberName: String,
    val currentRole: CampaignRole,
) : NavigationKey.WithResult<CampaignRole>

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
