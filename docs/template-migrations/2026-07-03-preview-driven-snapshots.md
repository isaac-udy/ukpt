# Preview-driven snapshot tests

Snapshot tests are now driven by `@Preview` composables. One `PreviewSnapshotTest` per client
module discovers every `@Preview` from the compiled classes (ComposablePreviewScanner) and renders
each one with Paparazzi. Hand-written per-screen snapshot test files are gone.

Rule changes:

- `UiLayer.Composable.screenContentSnapshotTest` is **removed**. Any `@ArchitectureException`
  referencing it must be updated or removed.
- `UiLayer.Composable.screenContentPreview` is **new**: every `[Name]ScreenContent` must be called
  from a `@Preview` composable in the same module.
- `UiLayer.Composable.previewsAreSnapshotTested` is **new**: a feature module with `@Preview`
  composables must have a `PreviewSnapshotTest` in its `androidHostTest` source set.

## Detection

The project is affected if any feature client module has hand-written snapshot tests
(`*ScreenSnapshotTest.kt` in `src/androidHostTest/`) or has no
`platform/snapshot/PreviewSnapshotTest.kt`.

## Migration

1. Version catalog: add `composablePreviewScanner = "0.9.0"` to `[versions]` and
   `composablePreviewScanner-android = { module = "io.github.sergio-sastre.ComposablePreviewScanner:android", version.ref = "composablePreviewScanner" }`
   to `[libraries]` (the file sync usually carries this; check it).
2. In every feature client module's `build.gradle.kts`:
   - Move `implementation(compose.preview)` from `androidMain` to `commonMain` dependencies (the
     unified `@Preview` is multiplatform since Compose 1.10).
   - Add `getByName("androidHostTest").dependencies { implementation(libs.composablePreviewScanner.android) }`.
3. Copy `feature/core/client/src/androidHostTest/kotlin/platform/snapshot/PreviewSnapshotTest.kt`
   from the template into each feature client module's `androidHostTest`, changing the scanned
   package to that module's package tree (`scanPackageTrees("feature.<name>")`).
4. For each `[Name]ScreenContent`, add a `@Preview` composable beside it (import
   `androidx.compose.ui.tooling.preview.Preview`) that renders the content inside `MaterialTheme`.
   Port each meaningful state from the old hand-written tests to its own `@Preview`.
5. Delete the hand-written `*ScreenSnapshotTest.kt` files and their goldens under
   `src/androidHostTest/snapshots/images/`.
6. Record and verify per client module:

```
./gradlew :feature:<name>:client:recordPaparazzi
./gradlew :feature:<name>:client:verifyPaparazzi
```

## Verification

`verifyArchitecture` passes (`screenContentPreview` and `previewsAreSnapshotTested` are green),
`verifyPaparazzi` passes per client module, and all targets compile (the `compose.preview` move
affects wasm/iOS compilation).
