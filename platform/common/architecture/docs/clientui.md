> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/clientui/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Client Ui](../src/main/kotlin/architecture/rules/clientui/ClientUi.kt)

`feature.[name].client.ui` — the client's outermost layer. It lives in `:client`: Compose UI
([Screens](#screen) and supporting composables), [ViewModels](#view-model), UI-state models,
and the serializable Navigation Keys ([Destinations](#destination)) that open the screens. A
Destination moves to `:api` (same package, a file move) only when another feature navigates to
it — that published key is the one part of this layer a second feature may see.

Everything the UI loads or mutates arrives through
[domain interfaces](clientdomain.md#domain-interface), provided by
[Repositories](clientdata.md#repository) in `client.data`. Server calls (via
[Services](serverservices.md#service-interface)) reach the screen the same way.

The layer rules below apply across the whole `feature.[name].client.ui` package.

##### Constructs

* [Screen](#screen)
* [Composable](#composable)
* [Destination](#destination)
* [View Model](#view-model)
* [View Model State](#view-model-state)
* [Ui Value Type](#ui-value-type)
* [Composition Local](#composition-local)

##### Rules

* A `client.ui` file may import another feature's `client.domain` only when the imported declaration is published to `:api`
    * **Why:** `client.domain` is private to its feature except for what the feature publishes to `:api` (`ClientDomain.pure`). The UI reaches another feature's domain the same way it reaches everything else cross-feature: through the published surface, never the feature's own `:client` module.
    * **Note:** Reuses the same published-FQN channel as `ClientDomain.pure` and `ServerDomain.pure` — publishing is moving the file, not changing the package.
* The `client.ui` layer must never implement `domain` interfaces
    * **Why:** Domain interfaces are the contract between presentation and persistence; implementations belong in `client.data` (Repositories) or `client.domain` (UseCases). A ViewModel that implements one couples two layers' lifecycles and makes the ViewModel un-injectable elsewhere.
    * **Note:** A parent reference is resolved through its file's imports and matched against the client's classified [Domain Interfaces](clientdomain.md#domain-interface) by fully-qualified name — an `:api`-declared parent often resolves to no source declaration, so resolution-based testing would silently skip exactly the published contracts.
* The `client.ui` layer must never depend on `data` or `services`
    * **Why:** The UI consumes `client.domain` interfaces only. Repositories (in `client.data`) call `server.services` on the UI's behalf; the UI must not reach either directly.
    * **Note:** Tested over the import's package segments, so a `data` or `services` package is out of bounds wherever it sits and whichever feature owns it.
* The `client.ui` layer must not use `koinInject`: all dependencies are injected through ViewModels
    * **Why:** Resolving from Koin inside a Composable bypasses the ViewModel as the single dependency surface, makes the screen untestable in snapshot tests (there is no Koin runtime), and re-resolves on every recomposition.
* A `client.ui` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Note:** A `client.ui` subsystem is a screen family the rest of the UI reaches through one entry point — an onboarding flow's steps, a sign-in provider's screens. It needs no matching `client.domain` subsystem: the rule restricts what a subsystem may import, not what must exist.
    * **Enforced by:** `ProjectRules.subsystemVisibility`
* A `client.ui` subsystem package imports `client.domain` only through the matching `client.domain` subsystem package, that package's direct children, and their ancestors
    * **Note:** A file at the layer root is unconstrained — it sees the whole of `client.domain`. Only a file inside a subsystem package is bound to the matching-subsystem rule.
    * **Enforced by:** `ProjectRules.subsystemMirrorsDomain`

##### Guidance

* The `client.ui` layer may depend on `client.domain`
* Dialog destinations communicate with their opener through navigation results, not shared state or callbacks
    * **Note:** A dialog destination follows the same screen conventions as any other destination — it has its own ViewModel, and the ViewModel performs the navigation actions (`complete`/`requestClose` via its `navigationHandle`). Composables never reference the navigation handle directly.
    * **Note:** A dialog destination is a `NavigationKey`; add `WithResult<R>` only when complete/close cannot carry the data. `complete()` means the user performed the associated action; `requestClose()` means the user cancelled. A confirmation dialog uses plain `NavigationKey` with `complete()`/`requestClose()` — the opener's `registerForNavigationResult(onCompleted = { … })` channel still fires on completion, and dismissal is a no-op. Use `NavigationKey.WithResult<R>` when the dialog returns data that complete/close alone cannot represent (e.g. an editor returning the edited value).
    * **Note:** Navigation results are held in-memory (`NavigationResultChannel.pendingResults`), so custom result types need no serializers-module registration — unlike managed-flow step results, which persist via `polymorphic(Any)`.
    * **Note:** Editor-style dialogs may own their submission (inline error/retry, `complete(result)` only on success) and complete with the fresh data so the opener updates without a refetch. Under `ProjectRules.noDirectAsyncStateConstruction` the opener cannot wrap a returned payload in `AsyncState.Success` directly, so in practice the result handler triggers a reload.

---

## [Screen](../src/main/kotlin/architecture/rules/clientui/Screen.kt)

A Composable function (or property-based `navigationDestination`) that defines the layout
and visual representation of a feature or portion of a feature.

### Dialog / Overlay Screens

A Screen may be presented as a dialog or overlay on top of the current screen, rather than
pushing onto the navigation backstack. These are governed by the `ClientUi.Screen.overlayViaDsl`
and `ClientUi.Screen.overlayViewModel` rules below. Regular screens that push to the backstack
should use the standard `@Composable fun` pattern; the property-based `navigationDestination`
DSL is for screens that need to declare custom metadata, such as `directOverlay()`. The
property name may end in `Screen` or `Destination`; both are accepted because the property is
the destination declaration site.

##### Requirements

* A Screen resides in `feature..client.ui..`
* A Screen is bound to its Destination via the `@NavigationDestination` annotation
* A Screen is named `[Name]Screen` (property-based screens may end in `Screen` or `Destination`)
* A Screen has a single parameter — the associated `[Name]ViewModel` (property form exempt)

##### Rules

* A Screen function must be annotated with `@Composable`
* A Screen function must be associated with exactly one ViewModel (and that ViewModel's associated ViewModel State). Rendering decisions must be derived from the ViewModel State, except purely visual, transient state — scroll position, animation, focus — which may stay in the composable
    * **Verification:** not automatically verifiable; enforced by review.
* A Screen function must observe the ViewModel's `state` property and use it to drive the UI
    * **Verification:** not automatically verifiable; enforced by review.
    * **Audited:** a test reports non-conforming code without ever failing.
* A dialog/overlay Screen must use the `navigationDestination` DSL with `metadata = { directOverlay() }`
    * **Verification:** not automatically verifiable; enforced by review.
* A Screen whose required data is an `AsyncState` must render `Idle`/`Loading`, `Success`, and `Error` explicitly — required data must not be read through `getOrNull()` with fallback labels that make unavailable data look successfully empty
    * **Verification:** not automatically verifiable; enforced by review.
* A Screen function must be paired with an `internal [Name]ScreenContent` composable in the same file
    * **Why:** The Screen function connects the ViewModel; the `ScreenContent` function takes only state and callbacks, so previews and snapshot tests can render every state without a ViewModel. Marking it `internal` lets the test source set call it; `private` makes the screen untestable.
* A ViewModel must be injected into its Screen using `viewModel()`, not `koinViewModel()`
    * **Why:** `viewModel()` ties the ViewModel's lifecycle to the navigation backstack entry: when the entry is popped, the ViewModel is cleared. `koinViewModel()` resolves through Koin and either scopes to the wrong lifecycle or returns a singleton, leaking state between screens or returning stale state on re-entry.

##### Guidance

* A Screen function should delegate user interactions that affect ViewModel State or navigation to the ViewModel; purely visual, transient interaction handling — scroll position, animation, focus — may stay in the composable
* A dialog/overlay Screen that needs a ViewModel should call `viewModel()` inside the `navigationDestination` block

##### Examples

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

---

## [Composable](../src/main/kotlin/architecture/rules/clientui/Composable.kt)

A `@Composable` function defined in the `..ui..` package that is not a [Screen](#screen).
Typically a sub-component used by one or more screens, an inline editor, a feature-specific
overlay, or a `@Preview` function.

* **Note:** `[Name]ScreenContent` companions (see `ClientUi.Screen.screenContentCompanion`)
  are non-Screen composables, which is why the snapshot rules live on this Construct.
  For reusable design-system primitives (buttons, fields), prefer a shared composable in
  `:platform:client:design`. Feature-local composables live alongside the Screen they support.

### Snapshot tests

Snapshots are preview-driven: `PreviewSnapshotTest` (in `src/androidHostTest/`) discovers the
`@Preview` composables in the module and renders each one with
[Paparazzi](https://github.com/cashapp/paparazzi), recording a golden image. Adding a
`@Preview` to a composable causes a snapshot to be generated for that composable.

* **Note:** Use the unified `@Preview` (`androidx.compose.ui.tooling.preview.Preview`)
  directly in common code; `compose.preview` must be a `commonMain` dependency. The same
  previews render in the IDE.
* **Note:** Add a `@Preview` per meaningful state as a screen grows. For async screens the meaningful states are: Loading, Error, populated Success, and legitimately-empty Success.
* **Note:** A screen's `@Preview`(s) live in the same file as the `[Name]ScreenContent` they
  render, next to the Screen — not gathered into a shared "screen previews" file.
* **Note:** A screen's golden should read as a **screenshot of the app on a device**, not a
  render on the harness canvas: wrap the preview's content in the design module's
  `UkptPreviewFrame`, which sizes the render to the project's primary viewport and pins the
  palette. The module's `PreviewSnapshotTest` renders in `RenderingMode.SHRINK`, cropping the
  golden to that frame; an unframed `fillMaxSize` preview still renders the full 960 dp
  canvas.
* **Note:** For one-off snapshot tests that aren't preview-driven, `SnapshotRule`
  (`dev.isaacudy.udytils.snapshot.SnapshotRule`) provides `snapshot.screen { }` and
  `snapshot.component { }`.
* **Note:** Record golden images after adding or changing a preview, then verify they match
  (goldens are committed under `src/androidHostTest/snapshots/images/`). Both tasks need
  `--no-configuration-cache` (the cache drops the R class from the test classpath):

  ```
  ./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
  ./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
  ```

##### Requirements

* A Composable resides in `feature..client.ui..`
* A Composable is not a Screen
* A Composable is annotated `@Composable`

##### Rules

* A `[Name]ScreenContent` composable must be called from a `@Preview` composable in the same file
    * **Why:** Previews are the snapshot surface: `PreviewSnapshotTest` renders every `@Preview` in the module, so a ScreenContent without a preview has no snapshot coverage. The preview must live in the same file as the ScreenContent it renders — co-locating it keeps each screen's preview next to the screen, discoverable and maintained with it, instead of drifting into a single shared "screen previews" file.
* Dialog primitives (`AlertDialog`, `BasicAlertDialog`, `DatePickerDialog`, `ModalBottomSheet`, `androidx.compose.ui.window.Dialog`) may only be invoked in a file that declares a dialog destination (one containing a `directOverlay` metadata marker)
    * **Why:** Dialogs are their own destinations: a `NavigationKey` rendered through Enro's overlay support, not an inline composable toggled by a boolean in screen state. Restricting dialog primitives to dialog-destination files makes embedded dialogs a build failure, not a review finding. Platform and design-system modules are exempt — they may define dialog primitives and wrappers.
    * **Note:** Detection is import-based: an import of any dialog primitive in a non-dialog-destination file is a violation, regardless of whether the call site is reached.
* A feature module that contains `@Preview` composables must have a `PreviewSnapshotTest` in its `androidHostTest` source set
    * **Why:** The scanner test is what turns previews into snapshots; without it, previews render in the IDE but nothing guards against visual regressions.
    * **Note:** Snapshot tests live under `src/androidHostTest/`, which the governed scope excludes; the test reads those files directly.
    * **Note:** A module opts in by extending `PreviewSnapshotTestCase` from `dev.isaacudy.udytils:snapshot`, which supplies the preview scanning and the golden layout.

---

## [Destination](../src/main/kotlin/architecture/rules/clientui/Destination.kt)

A serializable data class or object that represents the navigation contract for a particular
screen: the input parameters required by that screen (if any) and the output result type
provided by that screen (if any).

* **Note:** "Minimal data" means identifiers, not payloads. A Destination should accept a
  `User.Id` and let the Screen load the associated `User`, rather than accepting an entire
  `User`.

##### Requirements

* A Destination resides in `feature..client.ui..`
* A Destination is a class or object
* A Destination implements `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>`
* A Destination is named `[Name]Destination`
* A Destination is annotated `@Serializable`
* A Destination is declared in a file matching its name

##### Rules

* A Destination lives in the feature's `:client` module, and in `:api` only when another feature navigates to it
    * **Why:** `:api` is what features share through — with each other, or across the network. A Destination another feature has to name is one of those things; a Destination only its own feature names is not, and publishing it widens the feature's surface for nothing.  App modules are not the test. The shell, the admin client and the server wiring depend on the side modules directly and are meant to see and compose every feature's declarations, so a reference from `app/…` — a graph binding, a start destination, a shell decorator — never makes a Destination `:api`.
    * **Note:** The test measures the `:server` half: a Destination is client-side, so it is never declared in a `:server` module.
    * **Note:** Which of `:api` and `:client` holds a Destination is a judgement about who navigates to it, so it is read rather than tested; the default is `:client`, and a Destination moves to `:api` when a second feature needs it.

##### Guidance

* A Destination should accept the minimal data required to initialise the associated Screen
* A NavigationKey may carry a small optional presentation hint (for example a title hint) so minimal chrome stays informative while required data loads
    * **Note:** Identifiers remain the authoritative destination inputs. A presentation hint is non-authoritative, may be stale after restoration, and must not drive domain behavior. Loaded domain data always replaces hints.
    * **Note:** Whole domain payloads are not hints. A hint is a single display-ready value (a name, a title) that the opener already holds.

---

## [View Model](../src/main/kotlin/architecture/rules/clientui/ViewModel.kt)

A class that manages the UI state for a Screen and orchestrates calls to domain interfaces
to load data and perform side effects based on user actions.

* **Note:** The `navigation` handle is used to read Destination parameters and perform
  navigation. When closing a screen, use `NavigationHandle.close` when the user is cancelling
  or backing out, and `NavigationHandle.complete` when the user has successfully performed
  an action.

##### Requirements

* A View Model resides in `feature..client.ui..`
* A View Model extends `androidx.lifecycle.ViewModel`
* A View Model is named `[Name]ViewModel`
* A View Model declares its `state` property as a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type)
* A View Model has a `private val navigation` obtained via `navigationHandle<[Name]Destination>()`
* A View Model is declared in a file matching its name

##### Rules

* A ViewModel must expose a single public `state` property, or no public properties at all
* A ViewModel's `public`/`internal` functions must only return `Unit` (or omit a return type)
    * **Why:** State is the single source of truth; a public method that returns a value is a side channel around it.
* A ViewModel's `public`/`internal` functions must not be `suspend`
    * **Why:** A suspending public method makes the caller await work the ViewModel should own; on Android the awaiter (a composition scope, a `CompletableDeferred`) is lost on process death, silently dropping the result. Launch into `viewModelScope` and reflect the outcome in `state` instead.
* A ViewModel must not declare `private var` properties
    * **Why:** A mutable private field is a side channel around `state` (the source of truth) and is lost on process death — for example a `pendingX` captured across a navigation round-trip. Carry per-open context on the navigation itself (key fields, or `instance.metadata` via a `NavigationKey.MetadataKey`) so the result handler recovers it process-death-safe; put genuine UI state in `state`.
* A ViewModel must use `JobManager` to manage coroutines, never a `var job: Job?` reference
    * **Why:** `dev.isaacudy.udytils.coroutines.JobManager` provides cancel-then-replace semantics and ties every job to `viewModelScope`; manual `var job: Job?` tracking leaks the previous job and skips lifecycle cancellation.

##### Guidance

* A ViewModel should inject domain interfaces to load and manipulate domain objects
* When several asynchronously loaded inputs must be mutually coherent before the Screen is meaningful, the ViewModel should inject one client-domain interface exposing an immutable read projection, rather than independently collecting storage-shaped flows and merging them into UI state
    * **Why:** Independent collectors each update state on their own emission schedule, creating intermediate states where some fields are current and others are stale. A domain interface that combines the sources returns a single consistent snapshot per emission.
    * **Note:** A domain interface that composes several sources into one read model (`FlowOf...`) groups by consistency and failure boundary, not by screen. Compose in a UseCase when the combination is read-model logic; compose in a Repository when it is one data source's atomic projection.
    * **Note:** Live polling and optional resources may stay separate from the primary projection when their failure should not make the screen unusable.

---

## [View Model State](../src/main/kotlin/architecture/rules/clientui/ViewModelState.kt)

The complete, immutable representation of a Screen's data at a single point in time.

* **Note:** `AsyncState` covers action progress as well as loads: a "save" action can be an
  `AsyncState<Unit>`. Never directly construct `AsyncState.Loading`/`Success`/`Error`; use
  `AsyncState.fromSuspending`/`fromFlow`. That prohibition is enforced project-wide by
  `ProjectRules.noDirectAsyncStateConstruction`.

##### Requirements

* A View Model State resides in `feature..client.ui..`
* A View Model State is a class
* A View Model State is a `data class`
* A View Model State is named `[Name]State`
* A View Model State is declared in a file matching its name

##### Rules

* A ViewModel State object must be immutable (val properties only)
* A ViewModel State object must have a 1:1 relationship with a ViewModel type
    * **Verification:** not automatically verifiable; enforced by review.
* A ViewModel State object must not pair a progress-verb Boolean property with an error-synonym sibling — this is a hand-rolled async lifecycle; use `AsyncState<T>` / `AsyncState<Unit>` (via `fromFlow`/`fromSuspending`) or `UpdatableState<T>`
    * **Why:** A Boolean progress flag paired with an error property reimplements the state machine `AsyncState` already provides. The hand-rolled pair lacks idle/loading distinction, drops progress reporting, and forces every consumer to combine two fields that `AsyncState` keeps atomic.
* A ViewModel State object must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress
    * **Why:** Sentinel defaults (`""`, `emptyList()`, `false`) conflate a legitimate successful value with not-started, loading, and error. A required async value needs an explicit lifecycle state: `AsyncState<T>` for load-once data and action progress (`AsyncState<Unit>` for fire-and-observe actions like save/send/submit), or `UpdatableState<T>` when already-loaded data should stay visible through a refresh or error. An ordinary nullable or synchronous property remains valid when `null` has exactly one meaning.
    * **Verification:** not automatically verifiable; enforced by review.
    * **Audited:** a test reports non-conforming code without ever failing.
* A ViewModel State object must not define custom sealed types for loading/success/error; use `AsyncState<T>` instead
* A ViewModel State object must not contain dialog or sheet visibility flags (`show.*Dialog`, `.*DialogVisible`, `show.*Sheet`, `.*SheetVisible`) — dialog visibility is navigation state, not screen state
    * **Why:** A boolean flag that toggles a modal — whether rendered through an inline `AlertDialog`, a design-system dialog wrapper, or a bottom sheet — couples the modal's lifecycle to the screen's state object instead of to the navigation backstack. The modal is still a destination with its own ViewModel and state, regardless of how it is rendered. Making it a destination eliminates the flag; the destination's ViewModel performs `complete`/`requestClose`, and the opener consumes the outcome through a navigation result channel (add `WithResult<R>` only when complete/close cannot carry the data).
* A ViewModel State object's formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions
    * **Verification:** not automatically verifiable; enforced by review.

##### Guidance

* A ViewModel State object should be a transparent container for domain objects, not a lossy UI-level mapping
* A ViewModel State object should include `init` blocks that enforce invariants

##### Examples

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

---

## [Ui Value Type](../src/main/kotlin/architecture/rules/clientui/UiValueType.kt)

A small closed value type (enum, sealed class, or sealed interface) that lives in `..ui..`
and crosses feature boundaries, such as a `Slot` tag that one feature's ViewModel passes back
to another feature's screen.

* **Note:** If a value type grows behaviour, it stops being a value type. Promote it into a
  State, Destination, or domain object as appropriate.

##### Requirements

* An Ui Value Type resides in `feature..client.ui..`
* An Ui Value Type satisfies one of: {is an `enum class`, is `sealed`}
* An Ui Value Type has no member functions

---

## [Composition Local](../src/main/kotlin/architecture/rules/clientui/CompositionLocal.kt)

A top-level `Local…` [`CompositionLocal`](https://developer.android.com/jetpack/compose/compositionlocal)
declared in a `..ui..` package — the Compose-native channel for supplying ambient behaviour to a
composable without threading it as a parameter (for example a `LocalImageLoader` that lets a
reusable component reach a DI-provided dependency without every call site passing it down). The
value is provided once near the composition root and read by leaf composables.

##### Requirements

* A Composition Local resides in `feature..client.ui..`
* A Composition Local is a property
* A Composition Local is a top-level `Local…` val built via `compositionLocalOf` / `staticCompositionLocalOf`

##### Rules

* A composition local must be used as a dependency-access channel with an inert default (`null` / no-op), never as a back door for arbitrary mutable state
    * **Why:** An inert default lets a composable degrade gracefully when no provider is present, such as in snapshots and previews; using a composition local to smuggle mutable state around re-introduces the hidden coupling that threading dependencies through ViewModels avoids.
    * **Verification:** not automatically verifiable; enforced by review.
