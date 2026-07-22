# UKPT

This project is based on the [UKPT template](https://github.com/isaac-udy/ukpt). This file is
template-owned: it is synced by the `ukpt-template-update` skill, so don't edit it in downstream
projects — project-specific guidance belongs in AGENTS.md.

UKPT is a Kotlin Multiplatform project **template** — Compose UI, Enro navigation, Koin DI, urpc for the client/server contract, and a Ktor server. It targets Android, Desktop (JVM), Web (wasmJs), and iOS, plus a JVM server. It starts minimal; build features out from the documented patterns — `:feature:core` is the worked example to copy.

This file holds operational guidance (commands, toolchain, submodules) and pointers to the rules. The architecture rules are the source of truth in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md) — don't restate them here. The embedded builds carry their own repository guidance.

## Template versioning

Downstream projects update from this template with the `ukpt-template-update` skill, driven by `.ukpt/template.json` and [`docs/template-migrations/`](./docs/template-migrations/README.md). When a change affects code that only exists downstream — a convention change, an architecture rule added/renamed/tightened, a structural change — bump `templateVersion` in `.ukpt/template.json` and add a migration entry in the same commit (see the migrations README for the format). Version bumps and template-owned file changes don't need an entry.

Before committing a template change, run `./gradlew validateTemplate`. It checks the marker and
migration ordering/sections, shared agent guidance, Codex skill metadata, and Claude compatibility
links. The validator and rename-planner unit tests run with `./gradlew -p build-logic test`.

## Architecture

Follow the rules in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md), enforced by Konsist tests. Orientation:

- **Module groups**: `:app` (executable shells + DI wiring), `:feature` (vertical slices — `:api` contract, `:client` UI/logic, `:server` implementation), `:platform` (reusable infrastructure).
- **Feature axes**: `domain`, `ui` (client), `data` (client), `services` (`@Urpc` contracts in `:api`; `ServiceImpl`s + `services.internal` + `services.storage` on `:server`).
- The rules are a machine-readable **object catalog** in [`platform/common/architecture/src/main/kotlin/architecture/rules/`](./platform/common/architecture/src/main/kotlin/architecture/rules) (a `RuleGroup` object per layer in its own sub-package; one top-level `Construct<Group>` object per construct in its own file, listed in the group's `constructs`; a rule per property; the engine is the `dev.isaacudy.udytils:architecture-core` artifact from `embedded-udytils`). Every rule has a stable **path ID** — the object/property path, e.g. `DomainLayer.UseCase.noOverridingDefaults` — and an enforcement tag; [`docs/rule-index.md`](./platform/common/architecture/docs/rule-index.md) lists them all.
- The README and everything under `platform/common/architecture/docs/` are **generated**: rule statements and narrative come from `@Describe` annotations in the catalog itself; example blocks come from `<Construct>.examples.md` files next to the construct's `.kt` (e.g. `rules/data/Repository.examples.md`). The generator compiles a fixed per-layer structure (description → Requirements → Rules → Guidance → Examples). Edit the catalog or an examples file — never the generated files — then regenerate (see Testing).
- Exempt a declaration that genuinely can't conform with `@ArchitectureException(ruleIds = ["…"])` ([docs/exceptions.md](./platform/common/architecture/docs/exceptions.md)), only with human sign-off.
- Comment discipline: see [docs/code-comments.md](./docs/code-comments.md) — a comment must say something the code cannot.

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
- **Web** (dev server): `./gradlew :app:client:web:wasmJsBrowserDevelopmentRun --no-configuration-cache` — then open the served URL (the flag is required; see the config-cache note in Compiling)
- **Android**: run from Android Studio, or `./gradlew :app:client:android:installDebug` to a connected device/emulator
- **iOS**: open `app/client/ios/iosApp.xcodeproj` in Xcode and run (⌘R). There is no Gradle command: the Xcode project's "Compile Kotlin framework" build phase invokes `:app:client:common:embedAndSignAppleFrameworkForXcode`, which builds `App.framework` and puts it where the linker expects. Simulator builds are **Apple Silicon only** — `:app:client:common` declares `iosArm64` + `iosSimulatorArm64`, so the Xcode project excludes the `x86_64` simulator slice. Add an `iosX64()` target if you need Intel Macs.

## Building as an agent

Agents often run several at a time — subagents in one session, plus other sessions and other projects on the same machine. Nothing coordinates them, so each build has to be a good citizen on its own. The failure mode isn't a slow build, it's a machine that stops being usable: once memory is oversubscribed the box swaps, and swapping stalls everything, not just Gradle.

**Memory is the binding constraint, not CPU.** Each concurrent build needs its own daemon pair — the Gradle daemon (`org.gradle.jvmargs`) and the *separate* Kotlin compile daemon (`kotlin.daemon.jvmargs`), both 3 GB in [`gradle.properties`](./gradle.properties) — plus out-of-process Kotlin/Native for the iOS targets and a test JVM for Paparazzi. A daemon serves one build at a time, so a second concurrent build forks a second pair instead of sharing the first. At roughly 6 GB a build, a 16 GB machine tops out near two.

In order of leverage:

1. **Don't build what you don't need.** A docs, comment, or guidance-only change has no runtime surface — there is nothing a compile would verify. Skip it.
2. **Scope the build to the change.** Prefer one module's task (`:feature:core:client:compileKotlin`) over the full six-target sweep below. `verifyArchitecture` and `validateTemplate` are cheap; `assembleDebug`, the full sweep, `recordPaparazzi`, and anything Kotlin/Native are not. Never `clean` unless the task genuinely depends on it — the configuration and build caches are on, and `clean` discards exactly what makes a rebuild cheap.
3. **Don't run heavy builds concurrently.** When orchestrating subagents, let them read, analyse, and edit in parallel — that part is cheap — then run compilation and verification one at a time. Parallelism belongs in the editing phase, not the build phase.
4. **Throttle a background build** with `--max-workers=2`. Use a small *fixed* cap rather than a fraction of the core count: you can't know how many agents are running, and a per-agent fraction still multiplies by the number of agents, whereas a fixed cap bounds what each one contributes. A **foreground** build — one the user is waiting on — should not be throttled; it should finish fast.
5. **Never override daemon memory on an invocation.** Passing `org.gradle.jvmargs` or `kotlin.daemon.jvmargs` on the command line means the running daemon no longer matches, so a new one is forked — asking for less memory gets you more of it. Those belong in `gradle.properties`, one value shared by everyone. For the same reason keep flags identical across agents doing the same kind of work: daemons are matched on their JVM args, so inconsistent flags fragment the daemon pool.

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
The common module's Android / JVM / wasm targets compile transitively via the per-platform app modules; the iOS targets are built directly from `:app:client:common`. There is no `:app:client:ios` **Gradle** module — the iOS app is an Xcode project at `app/client/ios`, which consumes `App.framework` from `:app:client:common`. Compiling the iOS targets does **not** exercise the app: the Compose/Enro entry point (`iosMain/MainViewController.kt`) is only executed when the Xcode app runs, so a change to it must be verified by actually launching the app.

**Web (wasm) caveat — compiling is not enough.** `compileKotlinWasmJs` only type-checks; it does **not** catch wasm bundle/runtime failures — a `node:`-scheme import pulled in by a JVM-only dependency (e.g. `ktor-client-cio`), a missing ViewModel factory (`Factory.create … not implemented`), or the macOS `.DS_Store` IC-cache crash. For any web change, build the actual bundle and run it in a browser:
```
./gradlew :app:client:web:wasmJsBrowserDevelopmentWebpack --no-configuration-cache   # bundles via webpack — surfaces node:/IC errors
./gradlew :app:client:web:wasmJsBrowserDevelopmentRun --no-configuration-cache        # serves it — open the URL and confirm it renders
```
`--no-configuration-cache` is **required** on both: the Kotlin plugin's `KotlinWebpack` task holds a `Project` reference and an unserializable `SoftReference`, so it cannot be stored in the configuration cache (upstream). Everything else, including `compileKotlinWasmJs`, is cache-clean.

## Testing

- **Template integrity**: `./gradlew validateTemplate` — validates template metadata, migrations,
  shared agent guidance, and skills. `./gradlew -p build-logic test` runs the validator and safe
  project-rename planner's unit tests.
- **Architecture rules**: `./gradlew :platform:common:architecture:verifyArchitecture` — a standalone task that always re-executes (no `--rerun-tasks` needed; the module's plain `test` task runs nothing — the test classes are plugin-generated from the `UkptArchitecture` definition, not checked in). The suite reports **one nested test per rule** (`<Layer> › <Construct> › <rule>`), so a failure names the exact rule. After changing a rule or an examples file, regenerate the generated docs (README + `docs/`): `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.
- **UI snapshots** are preview-driven: every `@Preview` composable is discovered by `PreviewSnapshotTest` and snapshotted with Paparazzi (`UiLayer.Composable.screenContentPreview` requires a `@Preview` per ScreenContent). Record then verify goldens, per client module:
```
./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
```
`--no-configuration-cache` is **required** on both: under the configuration cache the R class is dropped from the test runtime classpath and every snapshot test dies with `ClassNotFoundException: <module>.R`.

  Goldens are **directory-grouped** by the preview's declaring package and function name, so a preview in `feature.ukpt.ui` lands at `src/androidHostTest/snapshots/images/feature/ukpt/ui/UkptScreenPreview.png`. `DirectorySnapshotHandler` (a custom Paparazzi `SnapshotHandler`) implements that layout; stock Paparazzi can only emit one long flat filename per golden. Two previews resolving to the same golden path fail fast at test-parameter creation rather than silently overwriting one another.
- **Unit tests**: per KMP module via the umbrella task, e.g. `./gradlew :feature:core:api:allTests :feature:core:client:allTests`; the server uses `./gradlew :app:server:test`.

## Server persistence (Postgres)

Server persistence uses the `dev.isaacudy.udytils.postgres` toolkit (Exposed + Flyway); conventions are in [docs/services.md](./platform/common/architecture/docs/services.md) (the `services.storage` section). The `:platform:server:postgres` module (Flyway migrations + codegen) is created when the first server feature needs persistence — until then the `services.storage` rules pass vacuously.
