> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Sources: @Describe annotations in the Kotlin catalog in `src/test/kotlin/architecture/rules/ui/` (narrative + rules), plus the `*.examples.md` files beside it.
> Regenerate with `./gradlew :platform:common:architecture:test -PupdateArchitectureDocs=true`.

# [Ui Layer](../src/test/kotlin/architecture/rules/ui/UiLayer.kt)

The `ui` axis spans `:api` and `:client`. **`:api` contents**: serializable Navigation Keys
(Destinations) — a feature's shared navigation entry points. **`:client` contents**: Compose UI
(Screens and supporting composables), ViewModels, and UI-state models. Everything the UI loads
or mutates arrives through [domain interfaces](domain.md#domain-interface), implemented by
[Repositories](data.md#repository) in `data` — which is also how server calls (via
[Services](services.md#service-interface)) reach the screen.

The layer rules below apply across the whole `feature.[name].ui` package.

##### Constructs

* [Screen](#screen)
* [Composable](#composable)
* [Destination](#destination)
* [View Model](#view-model)
* [View Model State](#view-model-state)
* [Ui Value Type](#ui-value-type)

##### Rules

* The `ui` layer is forbidden from implementing `domain` interfaces
    * **Why**: Domain interfaces are the contract between presentation and persistence — implementations belong in `data` (Repositories) or `domain` (UseCases). A ViewModel that implements one would couple two layers' lifecycles and make the ViewModel un-injectable elsewhere.
* The `ui` layer is forbidden from depending on `data` or `services`
    * **Why**: UI consumes `domain` interactors only — Repositories (in `data`) fan out to `services` (the cross-the-wire contract) on the UI's behalf. The UI must not reach either directly.
* The `ui` layer must not use `koinInject` — all dependencies are injected through ViewModels
    * **Why**: Resolving from Koin inside a Composable side-steps the ViewModel as the single dependency surface, makes the screen untestable in snapshots (no Koin runtime), and re-resolves on every recomposition.

##### Guidance

* The `ui` layer may depend on `domain`

---

## [Screen](../src/test/kotlin/architecture/rules/ui/Screen.kt)

A Composable function (or property-based `navigationDestination`) that defines the layout
and visual representation of a feature or portion of a feature.

### Dialog / Overlay Screens

A Screen that is presented as a dialog or overlay on top of the current screen, rather
than pushing onto the navigation backstack — governed by the `UiLayer.Screen.overlayViaDsl`
and `UiLayer.Screen.overlayViewModel` rules below. Regular screens that push to the
backstack should use the standard `@Composable fun` pattern; the property-based
`navigationDestination` DSL is specifically for screens that need to declare custom
metadata (such as `directOverlay()`). The property name may end in `Screen` or
`Destination` — both are accepted because the property *is* the destination declaration
site.

##### Requirements

* A Screen resides in `feature..ui..`
* A Screen is bound to its Destination via the `@NavigationDestination` annotation
* A Screen is named `[Name]Screen` (property-based screens may end in `Screen` or `Destination`)
* A Screen has a single parameter — the associated `[Name]ViewModel` (property form exempt)

##### Rules

* A Screen function must be annotated with `@Composable`
* A Screen function must have a 1:1 relationship with a ViewModel and ViewModel State
    * **Verification**: not automatically verifiable — enforced by review.
* A Screen function must observe the ViewModel's `state` property and use it to drive the UI
    * **Verification**: not automatically verifiable — enforced by review.
    * **Audited**: the test suite reports non-conforming code, without failing.
* A dialog/overlay Screen must use the `navigationDestination` DSL with `metadata = { directOverlay() }`
    * **Verification**: not automatically verifiable — enforced by review.
* A Screen function must be paired with an `internal [Name]ScreenContent` composable in the same file
    * **Why**: The Screen function plumbs the ViewModel; the `ScreenContent` function takes only state + callbacks so snapshot tests can render every state without a ViewModel. Marking it `internal` lets the host-test source set call it; `private` makes the screen untestable.
* A ViewModel must be injected into its Screen using `viewModel()`, not `koinViewModel()`
    * **Why**: `viewModel()` ties the ViewModel's lifecycle to the navigation backstack entry — when the entry is popped, the ViewModel is cleared. `koinViewModel()` resolves through Koin and either scopes to the wrong lifecycle or returns a singleton, leaking state between screens or returning stale state on re-entry.

##### Guidance

* A Screen function should delegate all user interaction handling to the ViewModel
* A dialog/overlay Screen that needs a ViewModel should call `viewModel()` inside the `navigationDestination` block

##### Examples

A dialog/overlay screen: the Destination lives in `:api`, and the property-based `navigationDestination` in `:client` declares `directOverlay()` metadata and resolves its ViewModel via `viewModel()` inside the block.

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

---

## [Composable](../src/test/kotlin/architecture/rules/ui/Composable.kt)

A `@Composable` function defined in the `..ui..` package that is **not** a Screen —
typically a sub-component used by one or more screens, an inline editor, or a
feature-specific overlay.

* **Note**: `[Name]ScreenContent` companions (see `UiLayer.Screen.screenContentCompanion`)
  are non-Screen composables, which is why the snapshot-test rule lives on this construct.
  For reusable design-system primitives (buttons, fields, marks), prefer a shared
  composable in `:platform:client:ui`. Feature-local composables live alongside the Screen
  they support, and may be `internal` so snapshot tests can drive them.

### Snapshot tests

A [Paparazzi](https://github.com/cashapp/paparazzi) host-side test that renders a Screen's
`[Name]ScreenContent` and records a golden image, catching visual regressions without a
device or emulator — enforced by `UiLayer.Composable.screenContentSnapshotTest` below.

* **Note**: Snapshot tests live in `feature/.../src/androidHostTest/` (the host-test
  source set under AGP 9.0's KMP library plugin) and use the `SnapshotRule` helper
  (`platform.snapshot.SnapshotRule`):
    * `snapshot.screen { ... }` — screen content / composables needing bounded layout
      constraints (`fillMaxSize()` etc.); renders in a fixed-size container.
    * `snapshot.component { ... }` — small, self-sizing composables; renders at content
      size with padding.
* **Note**: The composable under test must be `internal` (not `private`) so the host-test
  source set can reach it — the same constraint `UiLayer.Screen.screenContentCompanion`
  enforces. Add a `@Test` per meaningful state (loaded, empty, error, …) as a screen grows.
* **Note**: Record golden images after adding or changing a snapshot test, then verify they
  match (goldens are committed under `src/androidHostTest/snapshots/images/`):

  ```
  ./gradlew :feature:core:client:recordPaparazzi
  ./gradlew :feature:core:client:verifyPaparazzi
  ```

##### Requirements

* A Composable resides in `feature..ui..`
* A Composable is not a Screen
* A Composable is annotated `@Composable`

##### Rules

* A `[Name]ScreenContent` composable must be exercised by at least one snapshot test
    * **Why**: `ScreenContent` exists specifically so the screen body can be rendered from state + callbacks. Enforced softly — the test only checks that each ScreenContent is *called* from a `@Test` in an `androidHostTest` source set, not a minimum number of snapshots.

---

## [Destination](../src/test/kotlin/architecture/rules/ui/Destination.kt)

A serializable data class or object representing the navigation contract for a particular
screen; the input parameters required by that screen (if any) and the output result type
provided by that screen (if any).

* **Note**: "Minimal data" means identifiers, not payloads — a Destination should accept a
  `User.Id` and let the Screen load the associated `User`, rather than accepting an entire
  `User`.

##### Requirements

* A Destination resides in `feature..ui..`
* A Destination is a class or object
* A Destination implements `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>`
* A Destination is named `[Name]Destination`
* A Destination is annotated `@Serializable`
* A Destination is declared in a file matching its name

##### Rules

* A Destination may live in `:api` (shared entry point / server-driven) or `:client` (internal only)

##### Guidance

* A Destination should accept the minimal data required to initialise the associated Screen

---

## [View Model](../src/test/kotlin/architecture/rules/ui/ViewModel.kt)

A class that manages the UI state for a Screen and orchestrates calls to domain interfaces
to load data and perform side effects based on user actions.

* **Note**: The `navigation` handle is used to read Destination parameters and perform
  navigation. When closing/completing a screen, use `NavigationHandle.close` when the user
  is cancelling or backing out, and `NavigationHandle.complete` when the user has
  successfully performed an action.

##### Requirements

* A View Model resides in `feature..ui..`
* A View Model extends `androidx.lifecycle.ViewModel`
* A View Model is named `[Name]ViewModel`
* A View Model declares its `state` property as a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type)
* A View Model has a `private val navigation` obtained via `navigationHandle<[Name]Destination>()`
* A View Model is declared in a file matching its name

##### Rules

* A ViewModel exposes a single public `state` property, or no public properties at all
* A ViewModel's `public`/`internal` functions must only return `Unit` (or omit a return type)
* A ViewModel must use `JobManager` to manage coroutines — never hold `var job: Job?` references
    * **Why**: Manual `var job: Job?` tracking is error-prone: the previous job leaks if a new one starts before the old one completes, and lifecycle cancellation is easy to forget. `dev.isaacudy.udytils.coroutines.JobManager` handles cancel-then-replace and ties everything to `viewModelScope`.

##### Guidance

* A ViewModel should inject domain interfaces to load and manipulate domain objects

---

## [View Model State](../src/test/kotlin/architecture/rules/ui/ViewModelState.kt)

The complete, immutable representation of a Screen's data at a single point in time.

* **Note**: `AsyncState` covers action progress as well as loads — e.g. a "save" action as
  `AsyncState<Unit>`. Never directly construct `AsyncState.Loading`/`Success`/`Error` — use
  `AsyncState.fromSuspending`/`fromFlow`; that prohibition is enforced project-wide by
  `ProjectRules.noDirectAsyncStateConstruction`.

##### Requirements

* A View Model State resides in `feature..ui..`
* A View Model State is a class
* A View Model State is a `data class`
* A View Model State is named `[Name]State`
* A View Model State is declared in a file matching its name

##### Rules

* A ViewModel State object must be immutable (val properties only)
* A ViewModel State object must have a 1:1 relationship with a ViewModel type
    * **Verification**: not automatically verifiable — enforced by review.
* A ViewModel State object must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress
    * **Verification**: not automatically verifiable — enforced by review.
* A ViewModel State object must not define custom sealed types for loading/success/error — use `AsyncState<T>`
* A ViewModel State object's formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions
    * **Verification**: not automatically verifiable — enforced by review.

##### Guidance

* A ViewModel State object should be a transparent container for domain objects, not a lossy UI-level mapping
* A ViewModel State object should include `init` blocks that enforce invariants
    * **Audited**: the test suite reports non-conforming code, without failing.

##### Examples

A State that is a transparent container for domain objects plus calculated properties; display formatting lives with the Screen as a `@Composable` extension property, not in the State.

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

---

## [Ui Value Type](../src/test/kotlin/architecture/rules/ui/UiValueType.kt)

A small closed value type (enum, sealed class, or sealed interface) that lives in `..ui..`
and crosses feature boundaries — e.g. a `Slot` tag that one feature's ViewModel passes back
to another feature's screen.

* **Note**: If a value type grows behaviour, it stops being a value type — promote it into
  a State, Destination, or domain object as appropriate.

##### Requirements

* An Ui Value Type resides in `feature..ui..`
* An Ui Value Type satisfies one of: {is an `enum class`, is `sealed`}
* An Ui Value Type has no member functions
