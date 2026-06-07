# Architecture Rules Uplift Plan — ukpt ← reglyph (arcane-archivist)

**Status:** ✅ IMPLEMENTED (2026-06-07). Arch suite green — **45 tests, 0 failures** (baseline 31, 2 failing). Feature + app modules compile. Submodules updated (udytils → Postgres toolkit). Not committed; on branch `update-agp9-and-kmp-structure`. Decisions #1–3, #6 adopted as planned; **#4 reversed → Postgres adopted** (udytils `main` now ships the Postgres toolkit); AI + scheduled-jobs excluded.
**Goal:** Bring ukpt's `platform/common/architecture` rules + Konsist tests as close as possible to reglyph's more-evolved version, porting everything generic and explicitly excluding only what is genuinely reglyph-specific.
**Source of truth:** `/Users/isaacudy/work/arcane-archivist` (project name `reglyph`).

---

## Decisions locked (from review)

| # | Decision | Choice | Effect |
|---|----------|--------|--------|
| 1 | Services axis | **Adopt** | `data.services`/`data.storage` (server) → top-level **`services` axis**; `data` becomes **client-only**. Adds `services.internal` sealed-island visibility + cross-axis dependency tests. |
| 2 | urpc transport | **Adopt now** | Wire urpc (catalog + settings substitutions + KSP). `isServiceInterface` keeps the `@Urpc` requirement; R-FEAT-05 / R-SVC-03 guidance kept in README. |
| 3 | Exemption mechanism | **Adopt `@ArchitectureException`** | Replace the static `ArchitectureExceptions.kt` FQN list with a `src/main` annotation + `// architecture-exception:` build comments. Prerequisite for R-UI-33, R-SVC-09, R-SVC-13. |
| 4 | Persistence shape | **Adopt Postgres** *(updated)* | The Postgres + Exposed toolkit was moved into udytils `main`. After updating the submodule it's present (`:postgres-core/koin/codegen/gradle-plugin/embedded`). Adopt reglyph's `services.storage` Postgres rules: `isStorageRecord` (Row/Record/Insert), `isCodecObject`, `isMappingFunction`, `isStorageClass`, R-SVC-13. Document/Entity is dropped. |
| 5 | AI Assistant constructs | **Exclude** | No AI subsystem in ukpt; `AssistantConfig`/`AssistantTool` base types don't exist. Drop `isAssistantConfig`, `isInternalAssistantConfig`, the entire `Tools` sub-axis, and §4.4.5/§4.4.6 (R-SVC-22..27). |
| 5b | Scheduled-job constructs | **Exclude** *(confirmed)* | No job scheduler in ukpt. Drop FeatureLayer `isScheduledJob` (needs a missing `ScheduledJob` supertype) **and** the services-axis `isServicesJob`/`isInternalJob` (`*Job`) constructs. Easily re-added later; a future `*Job` will surface via the meta-test. |
| 6 | README rule-IDs + tags | **Adopt** | `R-<axis>-NN` IDs, `✅/🔶/📋/⚙️` enforcement tags, CI note, §6 rewrite. |
| 7 | Proceed | **Approved — implementing** | Plan reviewed; implementation underway. |

**Submodules:** `embedded-enro` already on latest `main` (`f7797403`, 3.0.0-beta02). `embedded-udytils` updated to latest `main` (`82b1c7f`) — adds the Postgres toolkit; the 4 new commits touch only `gradle/` + `postgres/*`, so ukpt's existing build (uses `core`/`ui`) is unaffected.

### Corrected facts (verified against the repos, override earlier assumptions)

- **`PresentableException` IS available** in ukpt (`embedded-udytils/core/.../error/PresentableException.kt`, part of `udytils-core` which ukpt already depends on). → **Keep** `PresentableException` in the domain `isException` construct and R-PROJ-02; do **not** strip it.
- **KSP is already wired** in ukpt (`ksp = 2.3.9`; `enro-processor` applied per-target). Adding `urpc-processor` follows the identical per-target pattern.
- **`@ArchitectureException` is JVM-only in reglyph** — used exclusively in `feature/core/server/src/main/` (never KMP `commonMain`); only `feature/core/server` consumes the arch module. Mirror this exactly. Latent limitation (shared with reglyph): a `commonMain` exemption can't import a `kotlinJvm` annotation — defer until needed, then relocate the annotation to a tiny KMP module.
- **`ModuleDependencyTests` hardcodes no module names** — pure `:app`/`:feature`/`:platform` prefix logic, so `:app:client:shared`-vs-`common` is irrelevant. Passes immediately on ukpt.
- **Sample `UkptScreen` will fail** the tightened R-UI-07/R-UI-11 (no ViewModel param, no `UkptScreenContent`). Must be fixed in Phase 5.
- **Pre-existing arch-test failure** is recorded in MEMORY.md — baseline the suite before/after so red isn't misattributed to this work.

---

## Scope summary

### ✅ Bring across as-is (general-portable)
- `ModuleDependencyTests.kt` (new; R-MOD-01/03/05/09/10) — self-contained `java.io.File` walker, no Konsist, passes immediately.
- Framework: `isInsideFunction()` + `validateLayer` filter; dual-form `primitiveTypeNames`; `hasFileNameMatchingDeclarationName()`; primitive-FQN short-circuit in `guessFullyQualifiedName`.
- Domain: `isDomainConstants` (4.1.7), `isDomainExtensionProperty` (4.1.5), R-DOM-19, loosened `@Throws` regex. **Keep** `PresentableException`.
- UI: R-UI-11 (`[Name]ScreenContent`), `isUiValueType` (4.2.6), own-file rules (R-UI-21/31), `Screen|Destination` dialog naming (R-UI-13), stateless-VM allowance.
- Feature/Data: `isDependencyRegistrationHelper`, `isClientDataInterface/Implementation`, standalone storage-inject test, rule-ID'd messages.

### 🔶 Bring with adaptation
- **`@ArchitectureException` mechanism** + build wiring (annotation in arch `src/main`, consumed by `feature/core/server`).
- **Services axis split** — new `ServicesLayer.kt`; `data`→client-only; sealed-island R-SVC-09; cross-axis dependency tests. **Keeps** `@Urpc` (urpc adopted). `services.storage` keeps Document/Entity (Postgres stripped).
- **README** rule-ID system + tags + §6 rewrite (strip Postgres ⚙️ + AI rules).
- **ProjectScope** AGP-9 `androidHostTest`/`androidUnitTest` excludes.

### ✅ Postgres (now in scope — udytils updated)
- Adopt reglyph's `services.storage` rules: `isStorageClass`, `isStorageRecord` (Row/Record/Insert), `isCodecObject`, `isMappingFunction`, R-SVC-13 (storage takes/returns `Row` types only).
- The ⚙️ codegen rules (R-SVC-14..21: generated `Table`/`Row` in `platform.server.postgres.tables`) are **not Konsist-enforced** (generated sources live under `build/generated`, never scanned) — they're README documentation only.
- **Deferred infra (not part of the rules port):** actually creating a `:platform:server:postgres` module (gradle plugin + Flyway migrations + `schema.sql` + Zonky) is follow-on work for when the first server feature needs persistence. The architecture rules land + pass vacuously until then.

### ❌ Exclude (reglyph-specific)
- **AI Assistant**: `isAssistantConfig`, `isInternalAssistantConfig`, the `Tools` sub-axis (`isAssistantTool`), R-SVC-22..27.
- **Scheduled jobs**: FeatureLayer `isScheduledJob` (missing `ScheduledJob` supertype) **and** services-axis `isServicesJob`/`isInternalJob`. Clearly flagged + trivially reversible.

---

## Phased implementation plan

> Ordering is dependency-aware. Phases 0–2 are low-risk and independent; Phase 3 unblocks exemption-gated rules; Phases 4–6 are per-layer; Phase 7 is the big services-axis split; Phase 8 is the README (last, so IDs match the implemented tests).

### Phase 0 — Infrastructure: wire urpc + arch-module consumption *(enables decisions 2 & 3)*
Logically independent of the Konsist ports, but required to fulfil "adopt urpc now" and to let `feature/core/server` import the exemption annotation.
- **`gradle/libs.versions.toml`**: add `urpc-protocol/client/server/koin/processor` library entries (mirror reglyph lines 99–103).
- **`settings.gradle.kts`**: add urpc dependency substitutions to the `includeBuild("embedded-udytils")` block (currently empty) — `urpc-protocol→:urpc:protocol`, `…client→:urpc:client`, `…server→:urpc:server`, `…koin→:urpc:koin`, `…processor→:urpc:processor`.
- **`feature/core/api/build.gradle.kts`**: apply `kotlinKsp`; add `urpc-processor` per-target (`kspAndroid/kspJvm/kspWasmJs/kspIosArm64/kspIosSimulatorArm64`) and `api(libs.urpc.protocol)` — mirroring the existing enro-processor wiring.
- **`feature/core/server/build.gradle.kts`**: add `implementation(libs.urpc.koin)` (transitively urpc-server + protocol; provides the `UrpcCall` scope qualifier) and `implementation(projects.platform.common.architecture)` (for the exemption annotation).
- **Out of scope here (runtime, not arch-tests):** the Ktor urpc *host* wiring in `:app:server`. Note it; needed before a real service runs, but no architecture test depends on it.

**Risk:** none to the test suite (no services exist yet → tests vacuous). Build must still resolve the new deps.

### Phase 1 — Framework & utils robustness *(no behavioral risk)*
- `definitions/KoBaseDeclaration.extensions.kt`: add `isInsideFunction()` (+ `KoContainingDeclarationProvider` import); expand `primitiveTypeNames` to dual short + `kotlin.*` forms.
- `definitions/KoScope.validateLayer.kt`: add `.filterNot { it.isInsideFunction() }` step (only *removes* declarations → cannot add failures).
- `definitions/DefinitionPredicate.kt`: add `hasFileNameMatchingDeclarationName()` (prereq for UI own-file rules).
- `utils/guessFullyQualifiedName.kt`: add the ~6-line primitive short-circuit + `primitiveTypeNames` import (correctness fix).
- Optional: comment-only deltas in `ConstructDefinition.kt`, `utils/NameWithGenerics.kt`, `utils/collectionTypeNames.kt` (byte-identical otherwise — no action needed).

### Phase 2 — `ModuleDependencyTests.kt` *(independent, instant green)*
- Drop in verbatim at `src/test/kotlin/architecture/ModuleDependencyTests.kt`. No Konsist/build changes; `java.io.File` + `kotlin.test` already available. The `// architecture-exception:` comment parser is self-contained (harmless before Kotlin-side exemptions migrate). ukpt satisfies R-MOD-01/03/05/09/10 today.

### Phase 3 — `@ArchitectureException` mechanism + ProjectScope
- **New** `src/main/kotlin/architecture/ArchitectureException.kt`: annotation `ruleIds: Array<String>`, `reason`, `trackingIssue`; `@Target(CLASS, FILE, FUNCTION, PROPERTY)`.
- Rewrite `src/test/kotlin/architecture/ArchitectureExceptions.kt`: add `isExempt(decl, vararg ruleIds)`, `isFileExempt(file, vararg ruleIds)`, private `exemptsAny` + `ARG_REGEX`/`ID_REGEX`, and an annotation-aware `isIgnored` (layer-membership). Keep a thin back-compat shim only if anything still references the old lists (nothing does today).
- **Build wiring**: confirm the arch module's `src/main` is a consumable artifact (it is — `kotlinJvm`); `feature/core/server` consumes it (added in Phase 0). Mirror reglyph (JVM-only consumption).
- `ProjectScope.kt`: add `!path.contains("/src/androidHostTest/")` and `!path.contains("/src/androidUnitTest/")` (AGP-9). Keep ukpt's literal `embedded-enro`/`embedded-udytils` excludes.

### Phase 4 — Domain layer
- `definitions/DomainLayer.kt`: add `isDomainConstants` + `isDomainExtensionProperty` (register in `layerDefinitions`); **keep** `PresentableException` in `isException`; drop the leftover debug `println`.
- `DomainLayerTests.kt`: add R-DOM-19 (UseCase must not override default fns); loosen R-DOM-10 `@Throws` regex (`Exception` as superclass of `CancellationException`); adopt R-DOM-NN messages. *(Hold the domain→services dependency test for Phase 7.)*

### Phase 5 — UI layer *(includes the sample-UI fix)*
- `definitions/UiLayer.kt`: add `isUiValueType`; broaden `isScreen` (property-form accepts `Screen` **or** `Destination` suffix); allow stateless ViewModels (0 public props or exactly `state`); own-file rules via `hasFileNameMatchingDeclarationName()` on `isViewModel` (R-UI-21), `isViewModelState` (R-UI-31), `isDestination`; split `isViewModelState` into `hasModifier(DATA)` + name-ends-`State` + immutable. Genericize doc-comment examples.
- `UiLayerTests.kt`: add R-UI-11 (every Screen has an `internal [Name]ScreenContent` `@Composable` in the same file); adopt R-UI-NN messages on the (unchanged-predicate) R-UI-02/27/28 tests. *(Hold R-UI-03's `|| services` clause for Phase 7; R-UI-33 AsyncState test moves to Phase 6 since it needs Phase 3's `isFileExempt`.)*
- **Fix the sample**: `feature/core/client/.../feature/ukpt/UkptScreen.kt` gains a `UkptViewModel` param + an `internal UkptScreenContent`; add `UkptViewModel` + `UkptState` (own files). Alternatively grant a tracked `@ArchitectureException` — but fixing it keeps the template exemplary.

### Phase 6 — Feature layer + axis-independent data/exemption-gated tests
- `FeatureLayerTests.kt`: adopt R-FEAT-03/04 IDs + messages + KDoc (the three DI predicates + meta-test are byte-identical → zero behavioral risk).
- `definitions/FeatureLayer.kt`: add `isDependencyRegistrationHelper` (Koin `Module` receiver; harmless).
- `definitions/DataLayer.kt`: add `isClientDataInterface`/`isClientDataImplementation`; keep `isRepository` verbatim.
- `DataLayerTests.kt`: add the standalone "storage classes must not inject domain/repositories/services" test; adopt R-`<axis>`-NN messages.
- `ArchitectureTests.kt`: add the R-UI-33 AsyncState-direct-construction test (now that Phase 3 provides `isFileExempt`); add the `isInsideFunction()` filter step; adopt rule-ID messages (R-PROJ-07 wildcards, etc.).

### Phase 7 — Services axis split *(the big one — decision 1)*
- **New** `definitions/ServicesLayer.kt` with sub-axes `services` / `.Internal` / `.Storage`:
  - `isServiceInterface` — **keep `@Urpc` requirement** (urpc adopted) + interface ending `Service`.
  - `isServiceImpl` (`isClass` + `ServiceImpl` + `internal`). **Drop** `isServicesJob` (job exclusion).
  - `services.internal` sealed-island constructs: `isInternalCoordinator`/`isInternalDataCarrier`/`isInternalInterface`/`isInternalException`/`isInternalObjectHelper`. **Drop** `isInternalAssistantConfig` (AI) → simplify `isInternalObjectHelper` to just "Is an object". **Drop** `isInternalJob` (job exclusion).
  - `.Storage` — **adopt Postgres** (`isStorageClass`, `isStorageRecord` Row/Record/Insert, `isMappingFunction`, `isCodecObject`), matching reglyph verbatim.
  - **No `.Tools` sub-axis** — AI excluded; drop the object and its `excludePackages` entry.
- `definitions/DataLayer.kt`: redefine **client-only** — remove nested `Services`, slim `Storage` to client-side Storage classes.
- `definitions/FeatureLayer.kt`: repoint `isServiceImplementation` → `ServicesLayer.isServiceInterface`; add `feature..services..` to `excludePackages`.
- `definitions/KoBaseDeclaration.extensions.kt`: add the `.services` branch to `featureNameFromRawPackage`.
- Tests: move @Throws/CancellationException service test, R-DATA-02, R-SVC-01 (services ↛ data.storage), R-SVC-25 (tools isolation), R-UI-03 `|| services`, domain↛services, and R-PROJ-02 (@Serializable exceptions — **keep `PresentableException`**) onto the new axis. Add the **R-SVC-09 sealed-island hierarchical-visibility test** (needs Phase 3's `isFileExempt`). Replace `validateDataLayerServices*` meta-tests with `validateServicesLayer{,Internal,Storage,Tools}Package`.

**Note:** All services-axis tests pass *vacuously* until `feature:core:server` has real service code — good for landing, but the rules are unexercised until then (mitigate by adding one sample service later).

### Phase 8 — README rule-IDs + tags + §6 rewrite *(last)*
- Adopt the `R-<axis>-NN` axis table (MOD/DOM/UI/DATA/SVC/FEAT/PROJ), `✅/🔶/📋/⚙️` tags, CI-enforcement note, and §6 rewritten around `@ArchitectureException` + `// architecture-exception:` comments.
- **Keep** urpc guidance (R-FEAT-05 `bindService`/`scope<UrpcCall>`, R-SVC-03) — urpc adopted.
- **Strip** Postgres ⚙️ codegen rules (R-SVC-14..21) and AI rules (R-SVC-22..27); renumber/strip dangling §-refs.
- Update `:app:client:common` → `:app:client:shared` text examples.
- Cross-check every `R-*` ID against the as-implemented test messages.

### Phase 9 *(optional)* — CI enforcement
- reglyph runs the suite in CI (`.github/workflows/pr-verification.yml`, `--rerun-tasks` to bypass Konsist's stale cache). ukpt has no `.github/workflows`. Optionally add a minimal workflow running `./gradlew :platform:common:architecture:test --rerun-tasks` so the README's CI note is real.

---

## Risks & gotchas
1. **Sample `UkptScreen` goes red** under tightened R-UI-07/R-UI-11 → fixed in Phase 5 (add ViewModel/State/ScreenContent) or exempt.
2. **Pre-existing arch-test failure** (MEMORY.md) → establish baseline first; don't misattribute.
3. **`@ArchitectureException` is JVM-only** (mirrors reglyph). A future KMP-`commonMain` exemption needs the annotation relocated to a tiny KMP module. Defer until needed.
4. **Services-axis rules are vacuous today** (`feature:core:server` empty) → latent mis-naming risk; add a sample service to exercise them.
5. **`ModuleDependencyTests` only parses typesafe `projects.*` accessors** — a future `project(":x")` string-notation dep would silently escape R-MOD checks. ukpt complies everywhere today.
6. **Rule-ID drift** → keep README (Phase 8) last and cross-check IDs against implemented test messages.
7. **urpc host wiring** (`:app:server` Ktor) is runtime, not covered by arch tests — track separately so the first real service actually serves.

---

## Open sub-decisions for implementation time
- **Annotation home**: keep in `kotlinJvm` arch `src/main` (reglyph-faithful, JVM-consumers only) vs a tiny KMP module (future-proofs commonMain exemptions). *Recommend: reglyph-faithful now.*
- **`services.tools` LayerDefinition**: keep as an empty target (meta-test lands cleanly) vs omit entirely until an AI/tooling subsystem exists. *Recommend: keep empty target.*
- **Phase 9 CI**: add now vs later. *Recommend: add a minimal workflow so the README CI note isn't aspirational.*
