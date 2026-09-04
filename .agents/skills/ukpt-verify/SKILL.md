---
name: ukpt-verify
description: >-
  Compile and build every platform (client + server) and run tests — the
  six-target compile sweep, architecture rule verification, validateTemplate and
  template integrity, Paparazzi snapshot golden recording/verification,
  per-module unit tests, iOS compile caveats, and --no-configuration-cache
  requirements. Use after making changes to verify correctness across all
  targets.
---

# ukpt-verify

Identifiers here use the template's UKPT identity (`UkptPreviewFrame`, `feature.ukpt`); projects rename these — the map is in `.ukpt/template.json`.

For web/wasm bundle and runtime verification, use the `ukpt-verify-web` skill — it covers the four
wasm-only failure modes that compilation misses.

## Compiling

Compile every platform (client + server) to verify correctness:
```
./gradlew :app:client:android:compileDebugKotlin \
          :app:client:android:checkDebugAarMetadata \
          :app:client:desktop:compileKotlin \
          :app:client:web:compileKotlinWasmJs \
          :app:client:common:compileKotlinIosArm64 \
          :app:client:common:compileKotlinIosSimulatorArm64 \
          :app:server:compileKotlin
```
`checkDebugAarMetadata` enforces each Android dependency's minimum AGP and compileSdk;
`compileDebugKotlin` never reaches it. It is cheap, and it is the only gate on the sweep that a
Compose or androidx bump can fail (`assembleDebug` runs it too, at the cost of a full build).

The common module's Android / JVM / wasm targets compile transitively via the per-platform app
modules; the iOS targets are built directly from `:app:client:common`. There is no
`:app:client:ios` **Gradle** module — the iOS app is an Xcode project at `app/client/ios`, which
consumes `App.framework` from `:app:client:common`. Compiling the iOS targets does **not** exercise
the app: the Compose/Enro entry point (`iosMain/MainViewController.kt`) is only executed when the
Xcode app runs, so a change to it must be verified by actually launching the app.

**Web (wasm) caveat — compiling is not enough.** `compileKotlinWasmJs` only type-checks; it does
**not** catch failures at wasm bundle time or runtime. For any web change, use the `ukpt-verify-web`
skill to bundle and serve the app in a browser.

## Configuration cache

`--no-configuration-cache` is **required** on these tasks:

- **`wasmJsBrowser*` tasks** (`wasmJsBrowserDevelopmentWebpack`, `wasmJsBrowserDevelopmentRun`): the
  Kotlin plugin's `KotlinWebpack` task holds a `Project` reference and an unserializable
  `SoftReference`, so it cannot be stored in the configuration cache (upstream). Everything else,
  including `compileKotlinWasmJs`, is cache-clean.
- **Paparazzi tasks** (`recordPaparazzi`, `verifyPaparazzi`): under the configuration cache the R
  class is dropped from the test runtime classpath and every snapshot test dies with
  `ClassNotFoundException: <module>.R`.
- **`allTests` on client modules**: a client module's `allTests` includes the snapshot host test, so
  it needs `--no-configuration-cache` too (same R-class failure as above).

## Testing

### Template integrity

`./gradlew validateTemplate` checks the marker and migration ordering/sections, shared agent
guidance, Codex skill metadata, Claude compatibility links, and that every file path, markdown link
and architecture rule ID a skill cites still resolves. Skills cite code they don't contain, so
moving code silently invalidates them — `validateTemplate` catches that.
`./gradlew -p build-logic test` runs the validator and safe project-rename planner's unit tests.

### Architecture rules

`./gradlew :platform:common:architecture:verifyArchitecture` is a standalone task that always
re-executes (no `--rerun-tasks` needed; the module's plain `test` task runs nothing — the test
classes are plugin-generated from the `UkptArchitecture` definition, not checked in). The suite
reports **one nested test per rule** (`<Layer> > <Construct> > <rule>`), so a failure names the
exact rule.

`verifyArchitecture` proves enforced rules and prints a one-line advisory audit summary.
`./gradlew auditArchitecture` prints the full advisory report (written to
the build directory as `audit.md` under `reports/architecture/`). Advisory findings are review
prompts, not build failures.
Semantic review of unverifiable rules — async state discipline, domain read projections,
loading/error rendering — is the `ukpt-architecture-review` skill. Ordinary build verification
stays separate from semantic review.

After changing a rule or an examples file, regenerate the generated documentation (README +
`docs/`):
```
./gradlew :platform:common:architecture:updateArchitectureDocumentation
```

### UI snapshots

UI snapshots are preview-driven: every `@Preview` composable is discovered by
`PreviewSnapshotTest` and snapshotted with Paparazzi (`ClientUi.Composable.screenContentPreview`
requires a `@Preview` per ScreenContent). Screen previews wrap their content in the design module's
`UkptPreviewFrame`, and each module's `PreviewSnapshotTest` renders in `RenderingMode.SHRINK`,
cropping the golden to that frame — so a screen golden reads as a device screenshot, not a render
padded to the harness canvas.

Record then verify goldens, per client module:
```
./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
```

Goldens are **directory-grouped** by the preview's declaring package and function name, so a preview
in `feature.ukpt.client.ui` lands at
`src/androidHostTest/snapshots/images/feature/ukpt/client/ui/UkptScreenPreview.png`.
`DirectorySnapshotHandler` implements this layout (stock Paparazzi only emits flat filenames). Two
previews resolving to the same golden path fail fast at test-parameter creation.

### Unit tests

Per KMP module via the umbrella task, e.g.:
```
./gradlew :feature:core:api:allTests :feature:core:client:allTests
```
A client module's `allTests` includes the snapshot host test, so it needs `--no-configuration-cache`
(see Configuration cache above).

Server coverage lives in **two** modules:
```
./gradlew :feature:core:server:test :app:server:test
```
The feature module holds nearly all of it; `:app:server:test` alone runs only the shell's own tests.
