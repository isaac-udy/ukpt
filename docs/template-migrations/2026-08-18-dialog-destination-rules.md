# Dialogs are their own navigation destinations

The template now enforces "dialogs are their own destinations": a dialog is a
`NavigationKey.WithResult<R>` rendered through `navigationDestination(metadata = {
directOverlayWithFade() }) { ... }`, opened from a ViewModel via `registerForNavigationResult<R>` /
`channel.open(key)`, with dismissal a no-op. Two new rules enforce it and one adds guidance:

- **`ClientUi.Composable.dialogPrimitivesOnlyInDialogDestinations`** (enforced) — `AlertDialog`,
  `BasicAlertDialog`, `DatePickerDialog`, `ModalBottomSheet`, and `androidx.compose.ui.window.Dialog`
  may only be imported in a file that declares a dialog destination (one carrying a `directOverlay`
  metadata marker). Detection is import-based, so an inline dialog primitive anywhere else in
  `feature..client.ui..` fails the build regardless of whether the call site is reached.
- **`ClientUi.ViewModelState.noDialogVisibilityFlags`** (enforced) — a ViewModel `State` may not
  declare a `show.*Dialog`, `.*DialogVisible`, `show.*Sheet`, or `.*SheetVisible` property. Dialog
  visibility is navigation state, not screen state.
- **`ClientUi.dialogsCommunicateViaResults`** (guidance) — a dialog destination follows the same
  screen conventions as any other destination: it has its own ViewModel, and the ViewModel performs
  the navigation actions (`complete`/`requestClose` via its `navigationHandle`). The opener registers
  a `NavigationResultChannel` via `ViewModel.registerForNavigationResult<R>` and opens the dialog
  with `channel.open(key)`.

## Detection

Any screen with an inline dialog primitive or a dialog-visibility flag is affected:

```bash
grep -rln "AlertDialog\|BasicAlertDialog\|DatePickerDialog\|ModalBottomSheet\|ui.window.Dialog" \
    --include="*.kt" -- '**/client/**' | xargs grep -L directOverlay
grep -rniE "show[A-Za-z]*Dialog|[A-Za-z]*DialogVisible|show[A-Za-z]*Sheet|[A-Za-z]*SheetVisible" \
    --include="*State.kt" -- '**/client/**'
```

## Migration

For each screen the detection step finds: extract the dialog into its own `NavigationKey.WithResult<R>`
destination file with `directOverlayWithFade()` metadata. The dialog destination follows the same
screen conventions as any other — give it its own ViewModel (with a `viewModelOf` registration in the
feature's Koin module), and have the ViewModel perform the navigation actions. Replace the visibility
flag and any dialog-specific fields on `State` with a `registerForNavigationResult<R>` channel on the
opener's ViewModel, and open it with `channel.open(key)` instead of flipping the flag. Give the
result type a meaningful shape (`Boolean` for a plain confirm, a data class when the dialog returns
something) — don't route it through the removed flag.

The `ClientUi.ViewModelState.noDialogVisibilityFlags`-generated examples in
`platform/common/architecture/docs/clientui.md` show the before/after (`ItemListState` /
`ItemListScreen` inline dialog → `ConfirmDeleteDestination` + result-channel opener). The
`ukpt-feature-slice` skill's "Dialogs are destinations, not screen state" section points at the
`:feature:core` worked example to copy the shape from directly:
`feature/core/client/src/commonMain/kotlin/feature/ukpt/client/ui/ConfirmResetDestination.kt` +
`ConfirmResetDialogScreen.kt` + `ConfirmResetViewModel.kt`, opened from
`UkptViewModel.onResetRequested()`.

## Verification

```bash
./gradlew :platform:common:architecture:verifyArchitecture
```

`dialogPrimitivesOnlyInDialogDestinations` and `noDialogVisibilityFlags` fail the build on any
screen not yet migrated, naming the file.
