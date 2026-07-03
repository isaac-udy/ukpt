---
name: ukpt-feature-slice
description: >-
  Scaffold a new feature vertical slice :feature:<name>:{api,client,server} in
  UKPT — the three build files, the Compose/Enro client (destination, screen,
  viewmodel, state, snapshot test), the server stub, the settings include, and
  the Koin DI + app wiring — modeled on :feature:core. Use when adding a new
  feature module to the project.
argument-hint: <feature-name>
---

# ukpt-feature-slice

Scaffold `:feature:<name>:{api,client,server}` modeled on the canonical `:feature:core`.
`templates.md` carries the build files + source skeletons; `:feature:core` is the living reference
to read when in doubt.

## Naming (read first — `:feature:core` has a deliberate mismatch)
`:feature:core` uses Gradle path segment `core` but Kotlin package `feature.ukpt` and type prefix
`Ukpt`. For a NEW feature, be **internally consistent** and use `<name>` everywhere:
- path `:feature:<name>:{api,client,server}`; Android namespaces `feature.<name>.{api,client,server}`;
- Kotlin package `feature.<name>`; DI vals `<name>ClientDependencies` / `<name>ServerDependencies`;
- types `<Name>Destination`, `<Name>Screen`, `<Name>ViewModel`, `<Name>State` (`<Name>` = PascalCase).

Do **not** copy the literal `ukpt`/`Ukpt` from core — substitute `<name>`/`<Name>`.

## Steps
1. **Module dirs + three `build.gradle.kts`** (templates.md §1–3). Substitute the namespace strings and
   the `projects.feature.<name>.*` accessors; keep everything else verbatim. Heed the gotchas below.
2. **`settings.gradle.kts`** — add three `include(...)` after the `:feature:core` block (templates.md §4).
3. **Client sources** (`feature.<name>.ui`): `<Name>Destination` in `:api`; `<Name>Screen` +
   `internal <Name>ScreenContent`, `<Name>ViewModel`, `<Name>State`, and `<name>ClientDependencies` in
   `:client` (templates.md §5).
4. **Snapshot tests (preview-driven)** — copy `SnapshotRule.kt` and `PreviewSnapshotTest.kt` from
   `feature/core/client/src/androidHostTest/kotlin/platform/snapshot/` into the new module's
   `androidHostTest/.../platform/snapshot/`, changing PreviewSnapshotTest's scanned package to
   `feature.<name>` (templates.md §6). The Screen template's `@Preview` (§5) is what gets
   snapshotted — `UiLayer.Composable.screenContentPreview` requires every ScreenContent to be
   called from a `@Preview`.
5. **Wire it up** — the easy-to-forget edits to existing files (templates.md §7 checklist):
   - `app/client/shared/build.gradle.kts` → `implementation(projects.feature.<name>.client)`.
   - `app/client/shared/.../App.kt` → add `<name>ClientDependencies` to `modules(...)` + its import.
   - `app/server/build.gradle.kts` → `implementation(projects.feature.<name>.server)` (if using the server).
   - Server DI: the template's `Server.kt` is a placeholder with **no** Koin host — standing that up is the
     `ukpt-urpc-service` skill's job (do it when the feature gets its first service, not at scaffold time).
6. **Verify** — compile sweep (all 6 targets, CLAUDE.md), record + verify Paparazzi for the new client
   module, and `./gradlew :platform:common:architecture:verifyArchitecture`. If the feature has web UI, run
   the `ukpt-verify-web` skill — a forgotten `viewModelOf` only crashes at runtime on wasm, invisible to compile.

## Load-bearing gotchas (from `:feature:core`)
- **KSP differs by module**: `:client` (enro) adds `kspCommonMainMetadata` **plus** every per-target `kspXxx`;
  `:api` (urpc) uses per-target only, **no** `kspCommonMainMetadata`. Don't cross-apply them.
- **Paparazzi R.jar hack** (client build): the verbatim `tasks.withType<Test>()` block puts the stub `R.jar`
  on the host-test *runtime* classpath, else tests fail with `ClassNotFoundException: feature.<name>.client.R`.
  Its intermediates path is keyed to the source-set name (`androidHostTest`), not the feature — copy unchanged.
- **`android.experimental.kmp.enableAndroidResources = true`** (client) — generates the R class for Paparazzi.
- **No `ktor-client-cio` in `commonMain`** — it pulls `node:net` and breaks the wasm bundle. CIO lives in
  `jvmMain`; web uses `ktor-client-js`.
- **`:server` depends on `libs.udytils.architectureAnnotations`** — solely so `@ArchitectureException` imports.
- **wasm ViewModel factory**: every screen VM must be `viewModelOf(::<Name>ViewModel)`-registered in
  `<name>ClientDependencies`, or web throws `Factory.create … not implemented` at runtime (the Koin-backed
  factory is installed once in `app/client/shared/.../UkptNavigation.kt` — don't regenerate it per feature).

## Rule cheat-sheet (canonical text in `platform/common/architecture/docs/` — search the ID)
- **`UiLayer.Screen.screenContentCompanion`** — `<Name>Screen` pairs with an `internal <Name>ScreenContent(state, …)`;
  **`UiLayer.Screen.viewModelInjection`** — inject the VM with `viewModel()` (not `koinViewModel()`);
  **`UiLayer.ViewModel.usesJobManager`** — VMs use udytils `JobManager`, never `var job: Job?`;
  **`UiLayer.Composable.screenContentPreview`** — every ScreenContent is called from a `@Preview` composable.
  **`UiLayer.Composable.previewsAreSnapshotTested`** — a module with `@Preview` composables has a `PreviewSnapshotTest`.
- **`FeatureRules.DependencyModule`** (construct) — DI is a `val <name>…Dependencies` module in `feature.<name>`;
  **`FeatureRules.constructorReferenceBindings`** — constructor-ref bindings.
- **`ServicesLayer.ServiceImpl`** (construct) — server-side services follow the `ukpt-urpc-service` skill.

## Reference
- The living template: `feature/core/{api,client,server}` (build files + `src/.../feature/ukpt/...`).
- App wiring: `app/client/shared/src/commonMain/kotlin/com/isaacudy/ukpt/{App.kt,UkptNavigation.kt}` and `settings.gradle.kts`.
- Skeletons + the wiring checklist: `templates.md` (this skill). For server services: the `ukpt-urpc-service` skill.
