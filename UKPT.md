# UKPT

This project is based on the [UKPT template](https://github.com/isaac-udy/ukpt). This file is
template-owned: it is synced by the `ukpt-template-update` skill, so don't edit it in downstream
projects — project-specific guidance belongs in CLAUDE.md.

UKPT is a Kotlin Multiplatform project **template** — Compose UI, Enro navigation, Koin DI, urpc for the client/server contract, and a Ktor server. It targets Android, Desktop (JVM), Web (wasmJs), and iOS, plus a JVM server. It starts minimal; build features out from the documented patterns — `:feature:core` is the worked example to copy.

This file holds operational guidance (commands, toolchain, submodules) and pointers to the rules. The architecture rules are the source of truth in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md) — don't restate them here. The submodules carry their own CLAUDE.md files.

## Template versioning

Downstream projects update from this template with the `ukpt-template-update` skill, driven by `.ukpt/template.json` and [`docs/template-migrations/`](./docs/template-migrations/README.md). When a change affects code that only exists downstream — a convention change, an architecture rule added/renamed/tightened, a structural change — bump `templateVersion` in `.ukpt/template.json` and add a migration entry in the same commit (see the migrations README for the format). Version bumps and template-owned file changes don't need an entry.

## Architecture

Follow the rules in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md), enforced by Konsist tests. Orientation:

- **Module groups**: `:app` (executable shells + DI wiring), `:feature` (vertical slices — `:api` contract, `:client` UI/logic, `:server` implementation), `:platform` (reusable infrastructure).
- **Feature axes**: `domain`, `ui` (client), `data` (client), `services` (`@Urpc` contracts in `:api`; `ServiceImpl`s + `services.internal` + `services.storage` on `:server`).
- The rules are a machine-readable **object catalog** in [`platform/common/architecture/src/main/kotlin/architecture/rules/`](./platform/common/architecture/src/main/kotlin/architecture/rules) (a `RuleGroup` object per layer in its own sub-package; one top-level `Construct<Group>` object per construct in its own file, listed in the group's `constructs`; a rule per property; the engine is the `dev.isaacudy.udytils:architecture-core` artifact from `embedded-udytils`). Every rule has a stable **path ID** — the object/property path, e.g. `DomainLayer.UseCase.noOverridingDefaults` — and an enforcement tag; [`docs/rule-index.md`](./platform/common/architecture/docs/rule-index.md) lists them all.
- The README and everything under `platform/common/architecture/docs/` are **generated**: rule statements and narrative come from `@Describe` annotations in the catalog itself; example blocks come from `<Construct>.examples.md` files next to the construct's `.kt` (e.g. `rules/data/Repository.examples.md`). The generator compiles a fixed per-layer structure (description → Requirements → Rules → Guidance → Examples). Edit the catalog or an examples file — never the generated files — then regenerate (see Testing).
- Exempt a declaration that genuinely can't conform with `@ArchitectureException(ruleIds = ["…"])` ([docs/exceptions.md](./platform/common/architecture/docs/exceptions.md)), only with human sign-off.

## Toolchain & constraints

- JDK target **11** (`jvmTarget = JVM_11`); Gradle **9.6.1** (wrapper). Exact dependency versions live in [`gradle/libs.versions.toml`](./gradle/libs.versions.toml).
- **AGP is pinned to 9.0.0** — the latest AGP IntelliJ supports (see `build-logic/src/main/kotlin/ukpt.kmp-library.gradle.kts`). Don't bump it past what the IDE understands.
- `embedded-enro` and `embedded-udytils` are **composite (`includeBuild`) builds**, so a Kotlin / Compose / AGP bump must stay compatible across all three repos — bump them together, not in isolation.

## Submodules

`embedded-enro` (navigation) and `embedded-udytils` (core / ui / urpc / postgres utilities) are **git submodules** that are actively developed and depended upon. After pulling, always sync them:
```
git submodule update --init --recursive
```
New code may rely on APIs that only exist in a newer submodule commit.

## Running

- **Desktop**: `./gradlew :app:client:desktop:run`
- **Server** (Ktor): `./gradlew :app:server:run`
- **Web** (dev server): `./gradlew :app:client:web:wasmJsBrowserDevelopmentRun` — then open the served URL
- **Android**: run from Android Studio, or `./gradlew :app:client:android:installDebug` to a connected device/emulator

## Compiling

After making changes, compile every platform (client + server) to verify correctness:
```
./gradlew :app:client:android:compileDebugKotlin \
          :app:client:desktop:compileKotlin \
          :app:client:web:compileKotlinWasmJs \
          :app:client:common:compileKotlinIosArm64 \
          :app:client:common:compileKotlinIosSimulatorArm64 \
          :app:server:compileKotlin
```
The common module's Android / JVM / wasm targets compile transitively via the per-platform app modules; the iOS targets are built directly from `:app:client:common` (ukpt has no separate `:app:client:ios` module).

**Web (wasm) caveat — compiling is not enough.** `compileKotlinWasmJs` only type-checks; it does **not** catch wasm bundle/runtime failures — a `node:`-scheme import pulled in by a JVM-only dependency (e.g. `ktor-client-cio`), a missing ViewModel factory (`Factory.create … not implemented`), or the macOS `.DS_Store` IC-cache crash. For any web change, build the actual bundle and run it in a browser:
```
./gradlew :app:client:web:wasmJsBrowserDevelopmentWebpack   # bundles via webpack — surfaces node:/IC errors
./gradlew :app:client:web:wasmJsBrowserDevelopmentRun        # serves it — open the URL and confirm it renders
```

## Testing

- **Architecture rules**: `./gradlew :platform:common:architecture:verifyArchitecture` — a standalone task that always re-executes (no `--rerun-tasks` needed; the module's plain `test` task runs nothing — the test classes are plugin-generated from the `UkptArchitecture` definition, not checked in). The suite reports **one nested test per rule** (`<Layer> › <Construct> › <rule>`), so a failure names the exact rule. After changing a rule or an examples file, regenerate the generated docs (README + `docs/`): `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.
- **UI snapshots** are preview-driven: every `@Preview` composable is discovered by `PreviewSnapshotTest` and snapshotted with Paparazzi (`UiLayer.Composable.screenContentPreview` requires a `@Preview` per ScreenContent). Record then verify goldens, per client module:
```
./gradlew :feature:core:client:recordPaparazzi
./gradlew :feature:core:client:verifyPaparazzi
```
- **Unit tests**: per KMP module via the umbrella task, e.g. `./gradlew :feature:core:api:allTests :feature:core:client:allTests`; the server uses `./gradlew :app:server:test`.

## Server persistence (Postgres)

Server persistence uses the `dev.isaacudy.udytils.postgres` toolkit (Exposed + Flyway); conventions are in [docs/services.md](./platform/common/architecture/docs/services.md) (the `services.storage` section). The `:platform:server:postgres` module (Flyway migrations + codegen) is created when the first server feature needs persistence — until then the `services.storage` rules pass vacuously.
