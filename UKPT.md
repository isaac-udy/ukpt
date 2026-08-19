# UKPT

This project is based on the [UKPT template](https://github.com/isaac-udy/ukpt). This file is
template-owned: it is synced by the `ukpt-template-update` skill, so don't edit it in downstream
projects — project-specific guidance belongs in AGENTS.md.

UKPT is a Kotlin Multiplatform project **template** — Compose UI, Enro navigation, Koin DI, urpc for the client/server contract, and a Ktor server. It targets Android, Desktop (JVM), Web (wasmJs), and iOS, plus a JVM server. It starts minimal; build features out from the documented patterns — `:feature:core` is the worked example to copy.

This file holds operational guidance (commands, toolchain, submodules) and pointers to the rules. The architecture rules are the source of truth in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md) — don't restate them here. The embedded builds carry their own repository guidance.

## Template versioning

Downstream projects update via the `ukpt-template-update` skill. When a change affects code that only exists downstream — a convention change, an architecture rule added/renamed/tightened, a structural change — bump `templateVersion` in [`.ukpt/template.json`](./.ukpt/template.json) and add a [`docs/template-migrations/`](./docs/template-migrations/README.md) entry in the same commit. Version bumps and template-owned file changes don't need an entry. Before committing any template change, run `./gradlew validateTemplate`.

## Architecture

Follow the rules in [`platform/common/architecture/README.md`](./platform/common/architecture/README.md), enforced by Konsist tests. Orientation:

- **Module groups**: `:app` (executable shells + DI wiring), `:feature` (vertical slices — `:api` contract, `:client` UI/logic, `:server` implementation), `:platform` (reusable infrastructure).
- **Feature packages are side-first**: `feature.<name>` holds the shared wire vocabulary; below it a side, then a layer — client: `client.ui`, `client.domain`, `client.data`; server: `server.services` (the `@Urpc` contract in `:api`, its `ServiceImpl` on `:server`), `server.domain`, `server.data`. Publication to `:api` is a module move that never changes the package.
- The rules are a machine-readable **object catalog** in [`platform/common/architecture/src/main/kotlin/architecture/rules/`](./platform/common/architecture/src/main/kotlin/architecture/rules) (a `RuleGroup` object per layer in its own sub-package; one top-level `Construct<Group>` object per construct in its own file, listed in the group's `constructs`; a rule per property; the engine is the `dev.isaacudy.udytils:architecture-core` artifact from `embedded-udytils`). Rules shared by the client/server twins of one construct are declared once on abstract base classes in [`rules/shared/`](./platform/common/architecture/src/main/kotlin/architecture/rules/shared) and instantiated per side. Every rule has a stable **path ID** — the object/property path, e.g. `ClientDomain.UseCase.noOverridingDefaults` — and an enforcement tag; [`docs/rule-index.md`](./platform/common/architecture/docs/rule-index.md) lists them all.
- The README and everything under `platform/common/architecture/docs/` are **generated**: rule statements and narrative come from `@Describe` annotations in the catalog; example blocks come from `<Construct>.examples.md` files in the group's package. Edit the catalog or an examples file — never the generated files — then regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.
- Exemptions require human sign-off: `@ArchitectureException(ruleIds = ["..."])` ([docs/exceptions.md](./platform/common/architecture/docs/exceptions.md)).
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

## Building as an agent

Agents often run several at a time — subagents in one session, plus other sessions and other projects on the same machine. Nothing coordinates them, so each build has to be a good citizen on its own. The failure mode isn't a slow build, it's a machine that stops being usable: once memory is oversubscribed the box swaps, and swapping stalls everything, not just Gradle.

**Memory is the binding constraint, not CPU.** Each concurrent build needs its own daemon pair — the Gradle daemon (`org.gradle.jvmargs`) and the *separate* Kotlin compile daemon (`kotlin.daemon.jvmargs`), both 3 GB in [`gradle.properties`](./gradle.properties) — plus out-of-process Kotlin/Native for the iOS targets and a test JVM for Paparazzi. A daemon serves one build at a time, so a second concurrent build forks a second pair instead of sharing the first. At roughly 6 GB a build, a 16 GB machine tops out near two.

In order of leverage:

1. **Don't build what you don't need.** A docs, comment, or guidance-only change has no runtime surface — there is nothing a compile would verify. Skip it.
2. **Scope the build to the change.** Prefer one module's task (`:feature:core:client:compileKotlin`) over the full six-target sweep (see `ukpt-verify`). `verifyArchitecture` and `validateTemplate` are cheap; `assembleDebug`, the full sweep, `recordPaparazzi`, and anything Kotlin/Native are not. Never `clean` unless the task genuinely depends on it — the configuration and build caches are on, and `clean` discards exactly what makes a rebuild cheap.
3. **Don't run heavy builds concurrently.** When orchestrating subagents, let them read, analyse, and edit in parallel — that part is cheap — then run compilation and verification one at a time. Parallelism belongs in the editing phase, not the build phase.
4. **Throttle a background build** with `--max-workers=2`. Use a small *fixed* cap rather than a fraction of the core count: you can't know how many agents are running, and a per-agent fraction still multiplies by the number of agents, whereas a fixed cap bounds what each one contributes. A **foreground** build — one the user is waiting on — should not be throttled; it should finish fast.
5. **Never override daemon memory on an invocation.** Passing `org.gradle.jvmargs` or `kotlin.daemon.jvmargs` on the command line means the running daemon no longer matches, so a new one is forked — asking for less memory gets you more of it. Those belong in `gradle.properties`, one value shared by everyone. For the same reason keep flags identical across agents doing the same kind of work: daemons are matched on their JVM args, so inconsistent flags fragment the daemon pool.

## Verification

After changes, compile every platform (client + server), not just the touched module — the `ukpt-verify` skill has the full sweep and test commands. Paparazzi and `wasmJsBrowser*` tasks require `--no-configuration-cache`.

## Reference

- `ukpt-run` — per-platform run commands, dev database setup and env switches.
- `ukpt-verify-web` — wasm bundle and runtime verification.
- `ukpt-ui-atlas` — `./gradlew generateUiAtlas`, manifest schema.
- `ukpt-server-packaging` — fat jar build, service-file checks, smoke test.
- `ukpt-feature-slice` — scaffold `:feature:<name>:{api,client,server}`.
- `ukpt-urpc-service` — add or change a `@Urpc` service end-to-end.
- `ukpt-design-system` — design system tokens and primitives.
