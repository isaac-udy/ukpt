---
name: ukpt-feature-slice
description: >-
  Scaffold a new feature vertical slice :feature:<name>:{api,client,server} in
  UKPT — the three build files, the Compose/Enro client (destination, screen,
  viewmodel, state, snapshot test), the server stub, the settings include, and
  the Koin DI + app wiring — modeled on :feature:core. Use when adding a new
  feature module to the project.
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

`<Prefix>` in the templates is a different thing: the **project's** type prefix, which the design
system's types carry (`<Prefix>Theme`, `<Prefix>Colors`). It is `Ukpt` in the template itself and
whatever the project was renamed to downstream — read `platform/client/design` to see which. It does
**not** vary per feature.

## Steps
1. **Module dirs + three `build.gradle.kts`** (templates.md §1–3). Substitute the namespace strings and
   the `projects.feature.<name>.*` accessors; keep everything else verbatim. Heed the gotchas below.
2. **`settings.gradle.kts`** — add three `include(...)` after the `:feature:core` block (templates.md §4).
3. **Client sources** (`feature.<name>.client.ui`): `<Name>Destination` in `:client` — the default
   home; move the file to `:api` (same package, no import churn) only when a **second feature**
   navigates to it, per `ClientUi.Destination.definedInApiOrClient` — an app-shell reference never
   forces the move. It pins `@SerialName("NavigationKey.<Name>Destination")` per
   `ProjectRules.serialNameEncodesEnclosingType`. `<Name>Screen` + `internal <Name>ScreenContent`,
   `<Name>ViewModel`, `<Name>State` in `:client`; `<name>ClientDependencies` at the feature root
   `feature.<name>` in `:client` (templates.md §5).
4. **Snapshot tests (preview-driven)** — the harness is the `dev.isaacudy.udytils:snapshot` artifact, so
   there is nothing to copy. Write the one `PreviewSnapshotTest` extending `PreviewSnapshotTestCase`
   in `RenderingMode.SHRINK`, scanning `feature.<name>` (templates.md §6). The Screen template's
   `@Preview` (§5) is what gets snapshotted — `ClientUi.Composable.screenContentPreview` requires
   every ScreenContent to be called from a `@Preview`, and the preview wraps it in
   `<Prefix>PreviewFrame` so the golden reads as a device screenshot rather than a render on the
   harness canvas.
5. **Wire it up** — the easy-to-forget edits to existing files (templates.md §7 checklist):
   - `app/client/common/build.gradle.kts` → `implementation(projects.feature.<name>.client)`.
   - `app/client/common/.../App.kt` → add `<name>ClientDependencies` to `modules(...)` + its import.
   - `app/server/build.gradle.kts` → `implementation(projects.feature.<name>.server)` (if using the server).
   - Server DI: the template's `Server.kt` is a placeholder with **no** Koin host — standing that up is the
     `ukpt-urpc-service` skill's job (do it when the feature gets its first service, not at scaffold time).
6. **Verify** — the six-target compile sweep (see UKPT.md §Compiling), record + verify Paparazzi for the new client
   module, and `./gradlew :platform:common:architecture:verifyArchitecture`. If the feature has web UI, run
   the `ukpt-verify-web` skill — a forgotten `viewModelOf` only crashes at runtime on wasm, invisible to compile.

## Load-bearing gotchas (from `:feature:core`)
- **KSP differs by module**: `:client` (enro) adds `kspCommonMainMetadata` **plus** every per-target `kspXxx`;
  `:api` (urpc) uses per-target only, **no** `kspCommonMainMetadata`. Don't cross-apply them.
- **Don't hand-wire Paparazzi** (client build): `ukpt.snapshot-testing` supplies the plugin, the host-test
  component and the stub-`R.jar` classpath fix; `ukpt.compose-library` supplies `androidResources` (which
  generates the R class Paparazzi resolves reflectively, and ships `composeResources` as APK assets).
  Restating any of them per module is how these drifted before they were conventions.
- **Read tokens, not literals** — a new screen's colours, spacing and text styles come from the
  project's design-system API, exposed by its design module; add
  `implementation(projects.platform.client.design)` to the `:client` module. On the scaffold that API is
  `<Prefix>Theme.colors`/`.typography` and `<Prefix>Spacing`, but a project that authored its own may
  expose a different accessor — read the design module (and the `ukpt-design-system` skill) to confirm
  before copying the template. `DesignSystemRules.noLiteralsInFeatureUi` audits for literal
  `Color(0x…)`/`.dp` in `feature..ui..`.
- **`:server` depends on `libs.udytils.architectureAnnotations`** — solely so `@ArchitectureException` imports.
- **Two web/wasm traps at scaffold time** — both pass compilation; the `ukpt-verify-web` skill has the full
  catalog and how to diagnose them. (1) Keep `ktor-client-cio` out of `commonMain`: it breaks the wasm
  bundle, so CIO goes in `jvmMain` and web uses `ktor-client-js`. (2) Register every screen VM with
  `viewModelOf(::<Name>ViewModel)` in `<name>ClientDependencies` (the Koin-backed VM factory itself lives
  once in `app/client/common/.../UkptNavigation.kt` — don't recreate it per feature), or web crashes at
  runtime with `Factory.create … not implemented`.

## Rule cheat-sheet (canonical text in `platform/common/architecture/docs/` — search the ID)
- **`ClientUi.Screen.screenContentCompanion`** — `<Name>Screen` pairs with an `internal <Name>ScreenContent(state, …)`;
  **`ClientUi.Screen.viewModelInjection`** — inject the VM with `viewModel()` (not `koinViewModel()`);
  **`ClientUi.ViewModel.usesJobManager`** — VMs use udytils `JobManager`, never `var job: Job?`;
  **`ClientUi.Composable.screenContentPreview`** — every ScreenContent is called from a `@Preview` composable.
  **`ClientUi.Composable.previewsAreSnapshotTested`** — a module with `@Preview` composables has a `PreviewSnapshotTest`.
- **`FeatureRules.DependencyModule`** (construct) — DI is a `val <name>…Dependencies` module in `feature.<name>`;
  **`FeatureRules.constructorReferenceBindings`** — constructor-ref bindings.
- **`ServerServices.ServiceImpl`** (construct) — server-side services follow the `ukpt-urpc-service` skill.

## Reference
- The living template: `feature/core/{api,client,server}` (build files + `src/.../feature/ukpt/...`).
- App wiring: `app/client/common/src/commonMain/kotlin/com/isaacudy/ukpt/{App.kt,UkptNavigation.kt}` and `settings.gradle.kts`.
- Skeletons + the wiring checklist: `templates.md` (this skill). For server services: the `ukpt-urpc-service` skill.
