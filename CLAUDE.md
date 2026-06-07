# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

UKPT is a Kotlin Multiplatform project **template** — Compose UI, Enro navigation, Koin DI, urpc for the client/server contract, and a Ktor server. It targets Android, Desktop (JVM), Web (wasmJs), and iOS, plus a JVM server. It starts minimal; build features out from the documented patterns.

This file holds only operational guidance (commands, submodules) and a pointer to the rules. The architecture rules themselves are the source of truth in the README below — don't restate them here.

## Architecture

Follow the architectural rules in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md). They are enforced by Konsist tests. Orientation:

- **Module groups**: `:app` (executable shells + DI wiring), `:feature` (vertical slices — `:api` contract, `:client` UI/logic, `:server` implementation), `:platform` (reusable infrastructure).
- **Feature axes**: `domain`, `ui` (client), `data` (client), `services` (`@Urpc` contracts in `:api`; `ServiceImpl`s + `services.internal` + `services.storage` on `:server`).
- Every rule has a stable ID (`R-<axis>-NN`) and an enforcement tag — search the README for an ID to find its canonical text. Exempt a declaration that genuinely can't conform with `@ArchitectureException` (README §6), only with human sign-off.

Verify the rules:
```
./gradlew :platform:common:architecture:test --rerun-tasks
```
`--rerun-tasks` is load-bearing — Konsist caches the project scope, and a stale cache can hide new violations.

## Submodules

`embedded-enro` (navigation) and `embedded-udytils` (core / ui / urpc / postgres utilities) are **git submodules** that are actively developed and depended upon. After pulling, always sync them:
```
git submodule update --init --recursive
```
New code may rely on APIs that only exist in a newer submodule commit.

## Compiling

After making changes, compile every platform (client + server) to verify correctness:
```
./gradlew :app:client:android:compileDebugKotlin \
          :app:client:desktop:compileKotlin \
          :app:client:web:compileKotlinWasmJs \
          :app:client:shared:compileKotlinIosArm64 \
          :app:client:shared:compileKotlinIosSimulatorArm64 \
          :app:server:compileKotlin
```
The shared module's Android / JVM / wasm targets compile transitively via the per-platform app modules; the iOS targets are built directly from `:app:client:shared` (ukpt has no separate `:app:client:ios` module).

## Snapshot tests

Screens are snapshot-tested with Paparazzi and this is enforced — see rule `R-UI-38` (README §4.2.1.2) for the `SnapshotRule` API and conventions. After adding or changing a snapshot test, record then verify the goldens:
```
./gradlew :feature:core:client:recordPaparazzi
./gradlew :feature:core:client:verifyPaparazzi
```

## Server persistence (Postgres)

Server persistence uses the `dev.isaacudy.udytils.postgres` toolkit (Exposed + Flyway); the conventions are in README §4.4.4. The `:platform:server:postgres` module that owns the Flyway migrations + codegen is created when the first server feature needs persistence — until then the `services.storage` rules pass vacuously.
