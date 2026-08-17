# UKPT

This project is based on the [UKPT template](https://github.com/isaac-udy/ukpt). This file is
template-owned: it is synced by the `ukpt-template-update` skill, so don't edit it in downstream
projects — project-specific guidance belongs in AGENTS.md.

UKPT is a Kotlin Multiplatform project **template** — Compose UI, Enro navigation, Koin DI, urpc for the client/server contract, and a Ktor server. It targets Android, Desktop (JVM), Web (wasmJs), and iOS, plus a JVM server. It starts minimal; build features out from the documented patterns — `:feature:core` is the worked example to copy.

This file holds operational guidance (commands, toolchain, submodules) and pointers to the rules. The architecture rules are the source of truth in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md) — don't restate them here. The embedded builds carry their own repository guidance.

## Template versioning

Downstream projects update from this template with the `ukpt-template-update` skill, driven by `.ukpt/template.json` and [`docs/template-migrations/`](./docs/template-migrations/README.md). When a change affects code that only exists downstream — a convention change, an architecture rule added/renamed/tightened, a structural change — bump `templateVersion` in `.ukpt/template.json` and add a migration entry in the same commit (see the migrations README for the format). Version bumps and template-owned file changes don't need an entry.

Before committing a template change, run `./gradlew validateTemplate`. It checks the marker and
migration ordering/sections, shared agent guidance, Codex skill metadata, Claude compatibility
links, and that every file path, markdown link and architecture rule id a skill cites still
resolves — skills describe code they don't contain, so moving code is what makes them rot. The
validator and rename-planner unit tests run with `./gradlew -p build-logic test`.

## Architecture

Follow the rules in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md), enforced by Konsist tests. Orientation:

- **Module groups**: `:app` (executable shells + DI wiring), `:feature` (vertical slices — `:api` contract, `:client` UI/logic, `:server` implementation), `:platform` (reusable infrastructure).
- **Feature packages are side-first**: the root `feature.<name>` holds the shared wire vocabulary (the models, exceptions and constants both sides speak); below it a side (`client`/`server`), then a layer — `client.ui`, `client.domain`, `client.data` on one side and `server.services` (the `@Urpc` contract in `:api`, its `ServiceImpl` on `:server`), `server.domain`, `server.data` on the other. Publication to `:api` is a module move that never changes the package.
- The rules are a machine-readable **object catalog** in [`platform/common/architecture/src/main/kotlin/architecture/rules/`](./platform/common/architecture/src/main/kotlin/architecture/rules) (a `RuleGroup` object per layer in its own sub-package; one top-level `Construct<Group>` object per construct in its own file, listed in the group's `constructs`; a rule per property; the engine is the `dev.isaacudy.udytils:architecture-core` artifact from `embedded-udytils`). Rules shared by the client/server twins of one construct are declared once on abstract base classes in [`rules/shared/`](./platform/common/architecture/src/main/kotlin/architecture/rules/shared) and instantiated per side. Every rule has a stable **path ID** — the object/property path, e.g. `ClientDomain.UseCase.noOverridingDefaults` — and an enforcement tag; [`docs/rule-index.md`](./platform/common/architecture/docs/rule-index.md) lists them all.
- The README and everything under `platform/common/architecture/docs/` are **generated**: rule statements and narrative come from `@Describe` annotations in the catalog itself; example blocks come from `<Construct>.examples.md` files in the group's package (e.g. `rules/clientdata/Repository.examples.md` and its `rules/serverdata/` twin). The generator compiles a fixed per-layer structure (description → Requirements → Rules → Guidance → Examples). Edit the catalog or an examples file — never the generated files — then regenerate (see Testing).
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
- **Server** (Ktor): `./gradlew :app:server:run` — boots against an embedded Postgres that keeps its data between runs; see Server persistence
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

**Web (wasm) caveat — compiling is not enough.** `compileKotlinWasmJs` only type-checks; it does **not** catch failures at wasm bundle time or runtime, so for any web change build the actual bundle and run it in a browser (the `ukpt-verify-web` skill catalogs the failure modes — `node:` imports, the missing ViewModel factory, the `.DS_Store` IC crash — and how to diagnose each):
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
- **UI snapshots** are preview-driven: every `@Preview` composable is discovered by `PreviewSnapshotTest` and snapshotted with Paparazzi (`ClientUi.Composable.screenContentPreview` requires a `@Preview` per ScreenContent). Screen previews wrap their content in the design module's `UkptPreviewFrame`, and each module's `PreviewSnapshotTest` renders in `RenderingMode.SHRINK`, cropping the golden to that frame — so a screen golden reads as a device screenshot, not a render padded to the harness canvas. Record then verify goldens, per client module:
```
./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
```
`--no-configuration-cache` is **required** on both: under the configuration cache the R class is dropped from the test runtime classpath and every snapshot test dies with `ClassNotFoundException: <module>.R`.

  Goldens are **directory-grouped** by the preview's declaring package and function name, so a preview in `feature.ukpt.client.ui` lands at `src/androidHostTest/snapshots/images/feature/ukpt/client/ui/UkptScreenPreview.png`. `DirectorySnapshotHandler` (a custom Paparazzi `SnapshotHandler`) implements that layout; stock Paparazzi can only emit one long flat filename per golden. Two previews resolving to the same golden path fail fast at test-parameter creation rather than silently overwriting one another.
- **Unit tests**: per KMP module via the umbrella task, e.g. `./gradlew :feature:core:api:allTests :feature:core:client:allTests` — a client module's `allTests` includes the snapshot host test, so it needs `--no-configuration-cache` too (same R-class failure as above). Server coverage lives in **two** modules: `./gradlew :feature:core:server:test :app:server:test` — the feature module holds nearly all of it; `:app:server:test` alone runs only the shell's own tests.

## Server persistence (Postgres)

Server persistence uses the `dev.isaacudy.udytils.postgres` toolkit (Exposed + Flyway); conventions are in [docs/serverdata.md](./platform/common/architecture/docs/serverdata.md) (the `server.data.storage` section). `:platform:server:postgres` owns the Flyway migrations (`src/main/resources/db/migration/`, empty until the first schema) and the codegen that turns them into Exposed `Table`/`Row` sources; `:platform:server:development` owns the dev-database scenarios.

`./gradlew :app:server:run` needs no database of your own — it starts an embedded Postgres, migrates it, seeds a brand-new one from `DefaultScenario`, and prints a banner saying where it is. The data lives in `app/server/build/dev-postgres/pg<major>/` and survives restarts (and `clean` therefore wipes it, as does `./gradlew :app:server:wipeDevDatabase`). The switches:

- `UKPT_DEV_DB` — `embedded` (persistent, the `run` default), `ephemeral` (a throwaway cluster on a random port), or anything else to connect to a real Postgres from `POSTGRES_URL` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_MAX_POOL_SIZE`. Set it in the invoking environment and `run` yields to you.
- `UKPT_DEV_DB_DIR` — where a persistent cluster lives; `run` points it at the build directory.
- `UKPT_DEV_SCENARIO` — names a `DevScenarios` entry to seed a **new** cluster with. Seeding is once-per-cluster; asking for a scenario over existing data fails rather than inserting on top.
- `PORT` — what the server listens on, default 8080.

## Server packaging

`./gradlew :app:server:buildFatJar` builds the deployable, minus the dev database: Zonky's embedded Postgres, its per-platform binaries and `:platform:server:development` are filtered out of the Shadow jar (`ukpt.server-packaging`). `run` and the tests are unaffected — they use the normal runtime classpath.

Two checks exist because a fat jar can silently drop things. `verifyRuntimeServiceFiles` (part of `check`, and gates `shadowJar`) fails when two runtime dependencies declare the same `META-INF/services` path, since only one copy survives packaging — `flyway-core` and `flyway-database-postgresql` do, which is why `app/server/src/main/resources/META-INF/services/` holds a hand-merged copy (its README says when to regenerate it). **Do not trust Shadow's `mergeServiceFiles()`**: it is called and, on 9.1.0, does not merge — verify by extracting the file from a built jar. `./gradlew :app:server:smokeTestFatJar` then boots the built jar the way a container would, on an OS-assigned port against a throwaway database, and asserts it migrates and answers; nothing else exercises the jar itself.
