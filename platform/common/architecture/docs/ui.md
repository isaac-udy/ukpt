<!--
  GENERATED FILE — do not edit.
  Narrative source: src/test/kotlin/architecture/rules/ui/UiLayer.md (structured blocks come from the rule catalog).
  Regenerate: UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test
-->

# The `ui` layer

The `ui` axis spans `:api` and `:client`. **`:api` contents**: serializable Navigation Keys (Destinations) — a feature's shared navigation entry points. **`:client` contents**: Compose UI (Screens and supporting composables), ViewModels, and UI-state models. Everything the UI loads or mutates arrives through [domain interfaces](domain.md#domain-interfaces), implemented by [Repositories](data.md#repositories) in `data` — which is also how server calls (via [Services](services.md#services-the-cross-the-wire-contract)) reach the screen.

## Layer rules

These apply across the whole `feature.[name].ui` package:

* **`UiLayer.mayDependOnDomain`** `📋 guidance` — May depend on `domain`
* **`UiLayer.noImplementingDomainInterfaces`** `✅ tested` — Forbidden from implementing `domain` interfaces
    * **Why**: Domain interfaces are the contract between presentation and persistence — implementations belong in `data` (Repositories) or `domain` (UseCases). A ViewModel that implements one would couple two layers' lifecycles and make the ViewModel un-injectable elsewhere.
* **`UiLayer.noDataServicesDeps`** `✅ tested` — Forbidden from depending on `data` or `services`
    * **Why**: UI consumes `domain` interactors only — Repositories (in `data`) fan out to `services` (the cross-the-wire contract) on the UI's behalf. The UI must not reach either directly.
* **`UiLayer.noKoinInject`** `✅ tested` — Must not use `koinInject` — all dependencies are injected through ViewModels
    * **Why**: Resolving from Koin inside a Composable side-steps the ViewModel as the single dependency surface, makes the screen untestable in snapshots (no Koin runtime), and re-resolves on every recomposition.

## Screens

* **Definition**: A Composable function (or property-based `navigationDestination`) that defines the layout and visual representation of a feature or portion of a feature.
* **Construct** `UiLayer.Screen` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..ui..`
    * Screen functions/properties must be bound to their Destination via the `@NavigationDestination` annotation
    * Screen functions are named `[Name]Screen`; property-based screens end in `Screen` or `Destination`
    * Screen functions must have a single parameter — the associated `[Name]ViewModel`
* **Rules**:
    * **`UiLayer.Screen.composableFunction`** `📋 guidance` — Screen functions must be annotated with `@Composable`
    * **`UiLayer.Screen.viewModelStateRelationship`** `📋 guidance` — Screen functions have a 1:1 relationship with a ViewModel and ViewModel State
    * **`UiLayer.Screen.observesState`** `📋 guidance` — Screen functions must observe the ViewModel's `state` property and use it to drive the UI
    * **`UiLayer.Screen.delegatesInteraction`** `📋 guidance` — Screen functions should delegate all user interaction handling to the ViewModel
    * **`UiLayer.Screen.overlayViaDsl`** `📋 guidance` — Dialog/overlay screens must use the `navigationDestination` DSL with `metadata = { directOverlay() }`
    * **`UiLayer.Screen.overlayViewModel`** `📋 guidance` — Dialog/overlay screens that need a ViewModel should call `viewModel()` inside the `navigationDestination` block
    * **`UiLayer.Screen.screenContentCompanion`** `✅ tested` — Screen functions must be paired with an `internal [Name]ScreenContent` composable in the same file
        * **Why**: The Screen function plumbs the ViewModel; the `ScreenContent` function takes only state + callbacks so snapshot tests can render every state without a ViewModel. Marking it `internal` lets the host-test source set call it; `private` makes the screen untestable.
    * **`UiLayer.Screen.viewModelInjection`** `✅ tested` — ViewModels must be injected into screens using `viewModel()`, not `koinViewModel()`
        * **Why**: `viewModel()` ties the ViewModel's lifecycle to the navigation backstack entry — when the entry is popped, the ViewModel is cleared. `koinViewModel()` resolves through Koin and either scopes to the wrong lifecycle or returns a singleton, leaking state between screens or returning stale state on re-entry.

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
* **Construct** `UiLayer.Destination` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..ui..`
    * is a class or object
    * Destinations must implement `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>`
    * name ends with `Destination`
    * annotated `@Serializable`
    * is declared in a file matching its name
* **Rules**:
    * **`UiLayer.Destination.minimalData`** `📋 guidance` — Destinations should accept the minimal data required to initialise the associated Screen
    * **`UiLayer.Destination.definedInApiOrClient`** `📋 guidance` — Destinations may live in `:api` (shared entry point / server-driven) or `:client` (internal only)
* **Note**: "Minimal data" means identifiers, not payloads — a Destination should accept a `User.Id` and let the Screen load the associated `User`, rather than accepting an entire `User`.

## ViewModels

* **Definition**: A class that manages the UI state for a Screen and orchestrates calls to domain interfaces to load data and perform side effects based on user actions.
* **Construct** `UiLayer.ViewModel` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..ui..`
    * ViewModels extend `androidx.lifecycle.ViewModel`
    * ViewModels must be named `[Name]ViewModel`
    * The `state` property is a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type)
    * ViewModels have a `private val navigation` obtained via `navigationHandle<[Name]Destination>()`
    * is declared in a file matching its name
* **Rules**:
    * **`UiLayer.ViewModel.singlePublicStateProperty`** `✅ tested` — ViewModels expose a single public `state` property, or no public properties at all
    * **`UiLayer.ViewModel.publicFunctionsReturnUnit`** `✅ tested` — `public`/`internal` functions on a ViewModel must only return `Unit` (or omit a return type)
    * **`UiLayer.ViewModel.injectsDomainInterfaces`** `📋 guidance` — ViewModels should inject domain interfaces to load and manipulate domain objects
    * **`UiLayer.ViewModel.usesJobManager`** `✅ tested` — ViewModels must use `JobManager` to manage coroutines — never hold `var job: Job?` references
        * **Why**: Manual `var job: Job?` tracking is error-prone: the previous job leaks if a new one starts before the old one completes, and lifecycle cancellation is easy to forget. `dev.isaacudy.udytils.coroutines.JobManager` handles cancel-then-replace and ties everything to `viewModelScope`.
* **Note**: The `navigation` handle is used to read Destination parameters and perform navigation. When closing/completing a screen, use `NavigationHandle.close` when the user is cancelling or backing out, and `NavigationHandle.complete` when the user has successfully performed an action.

## ViewModel State

* **Definition**: The complete, immutable representation of a Screen's data at a single point in time.
* **Construct** `UiLayer.ViewModelState` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..ui..`
    * is a class
    * is a `data class`
    * name ends with `State`
    * is declared in a file matching its name
* **Rules**:
    * **`UiLayer.ViewModelState.immutable`** `✅ tested` — ViewModel State objects must be immutable (val properties only)
    * **`UiLayer.ViewModelState.viewModelRelationship`** `📋 guidance` — ViewModel State objects have a 1:1 relationship with a ViewModel type
    * **`UiLayer.ViewModelState.usesAsyncState`** `📋 guidance` — ViewModel State objects must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress
    * **`UiLayer.ViewModelState.noCustomAsyncSealedTypes`** `📋 guidance` — ViewModel State objects must not define custom sealed types for loading/success/error — use `AsyncState<T>`
    * **`UiLayer.ViewModelState.transparentContainer`** `📋 guidance` — ViewModel State objects should be a transparent container for domain objects, not lossy UI-level mappings
    * **`UiLayer.ViewModelState.invariantInitBlocks`** `📋 guidance` — ViewModel State objects should include `init` blocks that enforce invariants
    * **`UiLayer.ViewModelState.formattingInScreen`** `📋 guidance` — Formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions
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
* **Construct** `UiLayer.Composable` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..ui..`
    * Is not a Screen
    * annotated `@Composable`
* **Rules**:
    * **`UiLayer.Composable.screenContentSnapshotTest`** `✅ tested` — Every `[Name]ScreenContent` composable must be exercised by at least one snapshot test
        * **Why**: `ScreenContent` exists specifically so the screen body can be rendered from state + callbacks. Enforced softly — the test only checks that each ScreenContent is *called* from a `@Test` in an `androidHostTest` source set, not a minimum number of snapshots.
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
* **Construct** `UiLayer.UiValueType` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * resides in `feature..ui..`
    * one of {is an `enum class`, is `sealed`}
    * Has no member functions
* **Note**: If a value type grows behaviour, it stops being a value type — promote it into a State, Destination, or domain object as appropriate.

