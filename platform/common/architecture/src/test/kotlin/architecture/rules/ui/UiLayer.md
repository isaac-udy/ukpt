# The `ui` layer

The `ui` axis spans `:api` and `:client`. **`:api` contents**: serializable Navigation Keys (Destinations) — a feature's shared navigation entry points. **`:client` contents**: Compose UI (Screens and supporting composables), ViewModels, and UI-state models. Everything the UI loads or mutates arrives through [domain interfaces](domain.md#domain-interfaces), implemented by [Repositories](data.md#repositories) in `data` — which is also how server calls (via [Services](services.md#services-the-cross-the-wire-contract)) reach the screen.

## Layer rules

These apply across the whole `feature.[name].ui` package:

{{rules:UiLayer}}

## Screens

* **Definition**: A Composable function (or property-based `navigationDestination`) that defines the layout and visual representation of a feature or portion of a feature.
{{construct:UiLayer.Screen}}

### Dialog / Overlay Screens

A Screen that is presented as a dialog or overlay on top of the current screen, rather than pushing onto the navigation backstack — governed by the `UiLayer.Screen.overlayViaDsl` and `UiLayer.Screen.overlayViewModel` rules above. Regular screens that push to the backstack should use the standard `@Composable fun` pattern; the property-based `navigationDestination` DSL is specifically for screens that need to declare custom metadata (such as `directOverlay()`). The property name may end in `Screen` or `Destination` — both are accepted because the property *is* the destination declaration site.

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

## Destinations (NavigationKeys)

* **Definition**: A serializable data class or object representing the navigation contract for a particular screen; the input parameters required by that screen (if any) and the output result type provided by that screen (if any).
{{construct:UiLayer.Destination}}
* **Note**: "Minimal data" means identifiers, not payloads — a Destination should accept a `User.Id` and let the Screen load the associated `User`, rather than accepting an entire `User`.

## ViewModels

* **Definition**: A class that manages the UI state for a Screen and orchestrates calls to domain interfaces to load data and perform side effects based on user actions.
{{construct:UiLayer.ViewModel}}
* **Note**: The `navigation` handle is used to read Destination parameters and perform navigation. When closing/completing a screen, use `NavigationHandle.close` when the user is cancelling or backing out, and `NavigationHandle.complete` when the user has successfully performed an action.

## ViewModel State

* **Definition**: The complete, immutable representation of a Screen's data at a single point in time.
{{construct:UiLayer.ViewModelState}}
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

## UI Composables (non-screen)

* **Definition**: A `@Composable` function defined in the `..ui..` package that is **not** a Screen — typically a sub-component used by one or more screens, an inline editor, or a feature-specific overlay.
{{construct:UiLayer.Composable}}
* **Note**: `[Name]ScreenContent` companions (see `UiLayer.Screen.screenContentCompanion`) are non-Screen composables, which is why the snapshot-test rule lives on this construct. For reusable design-system primitives (buttons, fields, marks), prefer a shared composable in `:platform:client:ui`. Feature-local composables live alongside the Screen they support, and may be `internal` so snapshot tests can drive them.

### Snapshot tests

A [Paparazzi](https://github.com/cashapp/paparazzi) host-side test that renders a Screen's `[Name]ScreenContent` and records a golden image, catching visual regressions without a device or emulator — enforced by `UiLayer.Composable.screenContentSnapshotTest` above.

* **Note**: Snapshot tests live in `feature/.../src/androidHostTest/` (the host-test source set under AGP 9.0's KMP library plugin) and use the `SnapshotRule` helper (`platform.snapshot.SnapshotRule`):
    * `snapshot.screen { ... }` — screen content / composables needing bounded layout constraints (`fillMaxSize()` etc.); renders in a fixed-size container.
    * `snapshot.component { ... }` — small, self-sizing composables; renders at content size with padding.
* **Note**: The composable under test must be `internal` (not `private`) so the host-test source set can reach it — the same constraint `UiLayer.Screen.screenContentCompanion` enforces. Add a `@Test` per meaningful state (loaded, empty, error, …) as a screen grows.
* **Note**: Record golden images after adding or changing a snapshot test, then verify they match (goldens are committed under `src/androidHostTest/snapshots/images/`):
```
./gradlew :feature:core:client:recordPaparazzi
./gradlew :feature:core:client:verifyPaparazzi
```

## UI value types

* **Definition**: A small closed value type (enum, sealed class, or sealed interface) that lives in `..ui..` and crosses feature boundaries — e.g. a `Slot` tag that one feature's ViewModel passes back to another feature's screen.
{{construct:UiLayer.UiValueType}}
* **Note**: If a value type grows behaviour, it stops being a value type — promote it into a State, Destination, or domain object as appropriate.
