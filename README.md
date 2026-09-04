# UKPT

**Udy Kotlin Project Template.** A Kotlin Multiplatform template: one codebase for Android, Desktop,
Web and iOS, plus a Ktor server. The architecture is enforced by tests, not by convention.

UKPT is a starting point, not a framework. It ships one working feature slice (`:feature:core`) and a
catalog of rules that describe how to grow from it. Copy the slice, follow the rules, delete what you
don't need.

## The stack

| Concern | Choice |
| --- | --- |
| UI | [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) |
| Navigation | [Enro](https://github.com/isaac-udy/Enro) |
| Dependency injection | [Koin](https://insert-koin.io/) |
| Client/server contract | `urpc` — typed RPC generated from a `@Urpc` interface ([udytils](https://github.com/isaac-udy/udytils)) |
| Server | [Ktor](https://ktor.io/) |
| Architecture rules | [Konsist](https://docs.konsist.lemonappdev.com/), via the udytils architecture system |
| UI snapshot tests | [Paparazzi](https://github.com/cashapp/paparazzi), driven off `@Preview` with [ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner) |

## Quick start

```bash
git clone <your-repo> && cd <your-repo>
git submodule update --init --recursive     # required, see Embedded libraries
```

```bash
./gradlew :app:client:desktop:run                          # Desktop
./gradlew :app:server:run                                  # Server, on :8080
./gradlew :app:client:android:installDebug                 # Android
./gradlew :app:client:web:wasmJsBrowserDevelopmentRun --no-configuration-cache   # Web
open app/client/ios/iosApp.xcodeproj                       # iOS — then run (⌘R)
```

The iOS app has no Gradle command: an Xcode build phase invokes
`:app:client:common:embedAndSignAppleFrameworkForXcode`, which builds `App.framework` from the shared
module. Simulator builds are Apple Silicon only.

To start a real project from the template, use the
[`ukpt-new-project`](.agents/skills/ukpt-new-project) skill. It renames the packages and app
identity, sets up the repository and submodules, and writes the `.ukpt/template.json` marker that
later template updates depend on.

Before renaming, it generates a classified, non-mutating inventory so UKPT-owned identifiers are
not caught in a global replacement:

```bash
./gradlew planProjectRename \
  -Pukpt.newProjectName=my-project \
  -Pukpt.newProjectPackage=com.example.myproject \
  -Pukpt.newProjectTypePrefix=MyProject
```

The report is written to `build/reports/ukpt/project-rename-plan.txt` with `REPLACE`, `REVIEW`, and
`KEEP` sections. Re-run with `-Pukpt.renameFailOnReplace=true` after renaming to fail if required
project-identity replacements remain.

## Architecture

**Read the [architecture README](platform/common/architecture/README.md)** for an overview of the
project structure and the rules that govern it.

The rules are defined in code, using the [udytils](https://github.com/isaac-udy/udytils) architecture
system over [Konsist](https://docs.konsist.lemonappdev.com/). They run as a test suite, and the
documentation is generated from them.

```bash
./gradlew :platform:common:architecture:verifyArchitecture               # run the tests
./gradlew :platform:common:architecture:updateArchitectureDocumentation  # regenerate the documentation
```

## Embedded libraries

Enro and udytils are consumed from source, as git submodules wired in as Gradle composite builds,
rather than as published artifacts. Both are written by the same author as the template and change
alongside it. Building them from source means a Kotlin, Compose or AGP bump can be made across all
three repositories together, and you can step into or patch library code directly from the app.

| Submodule | Repository | Provides |
| --- | --- | --- |
| `embedded-enro` | [isaac-udy/Enro](https://github.com/isaac-udy/Enro) | Navigation |
| `embedded-udytils` | [isaac-udy/udytils](https://github.com/isaac-udy/udytils) | Core and UI utilities, `urpc`, the architecture system, a Postgres toolkit |

`settings.gradle.kts` includes both with `includeBuild`, substituting every published coordinate for
the local project. The version catalog then declares them with no version:

```toml
enro-core    = { module = "dev.enro:enro" }               # no version.ref
udytils-core = { module = "dev.isaacudy.udytils:core" }   # no version.ref
```

With no version to resolve, these can only come from the submodule. There is no silent fallback to a
stale artifact.

Two consequences:

- **Sync submodules after pulling.** New code may need library APIs that only exist in a newer
  submodule commit.
  ```bash
  git submodule update --init --recursive
  ```
- **Toolchain bumps are a three-repository job.** Kotlin, Compose and AGP must stay compatible across
  ukpt, Enro and udytils. Bump them together.

### Using published versions instead

Both libraries are published to Maven Central, so this is entirely optional. To drop a submodule:

1. Remove its `includeBuild(...)` block from `settings.gradle.kts`.
2. Add a version to `gradle/libs.versions.toml` and point every affected entry at it.
3. Delete the submodule (`git submodule deinit`, remove it from `.gitmodules`).

```toml
[versions]
enro = "3.0.0-beta03"
udytils = "1.2.1"

[libraries]
enro-core    = { module = "dev.enro:enro", version.ref = "enro" }
udytils-core = { module = "dev.isaacudy.udytils:core", version.ref = "udytils" }
# ...and the remaining enro-*, udytils-*, urpc-* entries

[plugins]
udytilsArchitecture = { id = "dev.isaacudy.udytils.architecture", version.ref = "udytils" }
```

The trade is the one above: you lose lockstep toolchain bumps and the ability to patch library code
in place, and you pin to whatever the libraries last released.

## Toolchain

| | | |
| --- | --- | --- |
| Kotlin | `2.4.0` | |
| Gradle | `9.6.1` | wrapper |
| AGP | `9.2.1` | Follows Compose: androidx Compose 1.12 requires AGP 9.1.0+. IntelliJ IDEA 2026.1 syncs only up to 9.0.0, so use Android Studio for Android work. Must match the pin in both submodules. |
| Compose Multiplatform | `1.12.0` | |
| JVM target | `11` | |
| Android | minSdk 24, compileSdk 37 | |

Versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Build conventions are
precompiled script plugins in [`build-logic/`](build-logic).

## Building and testing

Compile every target after a cross-platform change:

```bash
./gradlew :app:client:android:compileDebugKotlin \
          :app:client:android:checkDebugAarMetadata \
          :app:client:desktop:compileKotlin \
          :app:client:web:compileKotlinWasmJs \
          :app:client:common:compileKotlinIosArm64 \
          :app:client:common:compileKotlinIosSimulatorArm64 \
          :app:server:compileKotlin
```

For web, compiling is not enough. `compileKotlinWasmJs` only type-checks, and will pass while the
bundle fails to load in a browser — a `node:` import pulled in by a JVM-only dependency, a missing
Koin ViewModel factory. Bundle it and open it. The
[`ukpt-verify-web`](.agents/skills/ukpt-verify-web) skill does that.

Snapshot tests are preview-driven. You do not write them: you write `@Preview` composables, and every
one is discovered and snapshotted. Goldens are grouped by package, for example
`snapshots/images/feature/ukpt/ui/UkptScreenPreview.png`.

```bash
./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
```

The Gradle configuration cache is on. Two task families are incompatible with it and need
`--no-configuration-cache`: the wasm browser and webpack tasks, and Paparazzi record/verify.
Everything else, including the full compile sweep, is cache-clean.

Template maintainers can validate the marker, migration documents, agent imports, shared skill
metadata, and Claude compatibility links with:

```bash
./gradlew validateTemplate
./gradlew -p build-logic test
```

## Template updates

A project created from UKPT can pull later template changes down.

- [`.ukpt/template.json`](.ukpt/template.json) records the template version a project is on.
- [`docs/template-migrations/`](docs/template-migrations) documents every change a file sync cannot
  express — a renamed rule, a changed convention, a restructured module — with how to detect it and
  what to do.
- The [`ukpt-template-update`](.agents/skills/ukpt-template-update) skill walks those entries in order
  and applies them.

## Coding agents

The repository works with [Codex](https://developers.openai.com/codex/) and
[Claude Code](https://claude.com/claude-code). [`AGENTS.md`](AGENTS.md) holds project-owned guidance,
[`UKPT.md`](UKPT.md) holds template-owned operational guidance, and [`CLAUDE.md`](CLAUDE.md) imports
both for Claude Code. The architecture rules double as machine-readable instructions.

Five shared skills cover repetitive work. Their canonical home is `.agents/skills/`, which Codex
discovers directly; `.claude/skills/` contains links to the same directories so Claude Code uses
the identical instructions:

| Skill | Use it to |
| --- | --- |
| [`ukpt-new-project`](.agents/skills/ukpt-new-project) | Turn a fresh copy of the template into a renamed, real project |
| [`ukpt-feature-slice`](.agents/skills/ukpt-feature-slice) | Scaffold `:feature:<name>:{api,client,server}` and wire it up |
| [`ukpt-urpc-service`](.agents/skills/ukpt-urpc-service) | Add a client/server `@Urpc` service end to end |
| [`ukpt-verify-web`](.agents/skills/ukpt-verify-web) | Bundle and run the web target, rather than only type-checking it |
| [`ukpt-template-update`](.agents/skills/ukpt-template-update) | Pull the latest template version into a project |

None of this is required. The project is a normal Gradle build.

## License

[Apache 2.0](LICENSE).
