---
name: verify-web
description: >-
  Validate a web/wasm (wasmJs) client change by actually bundling (webpack) and
  serving it in a browser — because `compileKotlinWasmJs` only type-checks and
  misses bundle/runtime failures. Use when a change touches the web client, Ktor
  client engines, the navigation entry point, or ViewModel/Koin wiring, or
  whenever the web build or render needs confirming.
---

# verify-web

`compileKotlinWasmJs` only **type-checks**. The four failure modes below all pass
compilation and only bite at **bundle time** (webpack) or **runtime** (browser).
A green compile is necessary but not sufficient — you must bundle *and* serve.

## Two gates

**1. Bundle gate** — catches `node:` imports (mode 1) and the `.DS_Store` IC crash (mode 4):
```
./gradlew :app:client:web:wasmJsBrowserDevelopmentWebpack
```
Ergonomics: `bash .claude/skills/verify-web/run-bundle-check.sh` runs this and flags the two build-time signatures for you.

**2. Runtime gate** — catches the navigation entry-point gap (mode 2) and the missing ViewModel factory (mode 3). These are **invisible to webpack**:
```
./gradlew :app:client:web:wasmJsBrowserDevelopmentRun
```
Open the served URL, confirm the page actually renders (the `<body>` gets populated by `ComposeViewport`), open the browser console, and **navigate to the changed screen(s)** — mode 3 only fires when a `viewModel()` destination first composes.

**Done when:** webpack succeeds **and** the served page renders with no console errors on the affected screens.

## Failure signatures → cause → fix

| Symptom | Gate | Cause | Fix |
|---|---|---|---|
| `UnhandledSchemeError` / `node:net` (or other `node:`) | bundle | A JVM/native-only dep reached the wasm classpath — classically `ktor-client-cio` → `ktor-network`. | Web uses **`ktor-client-js`** only; keep engine deps out of `commonMain`/`wasmJsMain`. See `app/client/web/build.gradle.kts` and the comments in `app/client/shared/build.gradle.kts` / `feature/core/client/build.gradle.kts`. |
| `IC internal error: can not find removed library name` | bundle | macOS wrote `.DS_Store` into the Kotlin/Wasm klib IC cache. | `find app/client/web/build -name .DS_Store -delete`, then re-run. A `doFirst` purge hook already guards `*WasmJs*` tasks in `app/client/web/build.gradle.kts`. |
| Blank page / navigation & URLs don't work | runtime | The web entry point didn't install the nav controller or wrap content in `EnroBrowserContent`. | `Main.kt` must call `UkptNavigation.installNavigationController(document)` then `ComposeViewport(document.body!!) { EnroBrowserContent { App() } }`. |
| Console: `Factory.create(...) is not implemented` | runtime | wasm has no reflection, so the default ViewModel factory can't construct the VM. | Register the ViewModel with `viewModelOf(::YourViewModel)` in a Koin module loaded by `KoinApplication` in `App()`. The Koin-backed factory lives in `UkptNavigation.kt`. |

## Reference (read only if you hit the relevant mode)
- `app/client/web/build.gradle.kts` — wasmJs config, the `ktor-client-js` dep, the `.DS_Store` purge hook.
- `app/client/web/src/wasmJsMain/kotlin/com/isaacudy/ukpt/Main.kt` — the web entry point (mode 2).
- `app/client/shared/src/commonMain/kotlin/com/isaacudy/ukpt/UkptNavigation.kt` + `App.kt` — Koin-backed VM factory + Koin startup (mode 3).
- `feature/core/client/src/commonMain/kotlin/feature/ukpt/UkptClientDependencies.kt` — `viewModelOf` registration example (mode 3).
