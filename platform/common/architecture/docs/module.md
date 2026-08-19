> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/module/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Module Rules](../src/main/kotlin/architecture/rules/module/ModuleRules.kt)

The project is organized into three root-level module groups: `:app`, `:feature`, and
`:platform`. The dependency rules between them are **module-graph rules**: they are tested
against the module dependency graph parsed from the `build.gradle.kts` files, not against
Kotlin source. Build-file exemptions use the `// architecture-exception:` comment (see
[architecture exceptions](exceptions.md)).

## `:app` (Application shells)

* **Purpose**: Final executable entry points and dependency injection (DI) wiring.
* **Structure**: May contain sub-groups (e.g., `:app:admin`, `:app:customer`) if multiple applications are built from the same codebase.
* **Child Modules**: Each app contains a `:client` (Mobile/Desktop/Web) and/or a `:server` (Ktor executable).
    * **Client structure (AGP 9.0)**: AGP 9.0 does not allow one module to be both a KMP library and a `com.android.application`, so `:app:client` is a group: `:app:client:common` is a KMP library holding the shared UI, navigation, DI wiring, and the iOS framework entry point; `:app:client:android`, `:app:client:desktop`, and `:app:client:web` are thin application modules holding only an entry point and platform packaging, each depending on `:app:client:common`.
* **Constraints**: Must not contain business logic. Limited to infrastructure configuration and DI module aggregation.

## `:feature` (Vertical slices of functionality)

* **Purpose**: Encapsulated feature-specific functionality.
* **Sub-Modules**:
    * **`:api`**: Mandatory. Contains the shared contract.
    * **`:client`**: Optional. Contains UI and client-side logic.
    * **`:server`**: Optional. Contains server-side implementation.
* **Notes**:
    * Small projects may start with all feature code in `:feature:core`. Code inside `:feature:core` still uses per-feature packages (`feature.auth`, `feature.invoices`) rather than `feature.core`, and one feature's packages may depend on another's only through declarations in the `:api` module (enforced by `ModuleRules.crossFeatureCodeViaApi`). This keeps each feature liftable into its own `:feature:[name]` module later.
    * `:client` and `:server` modules are optional, but at least one of the two should exist for every feature.

## `:platform` (Infrastructure)

* **Purpose**: Reusable, non-feature-specific capabilities.
* **Sub-Groups**:
    * **`:common`**: Code shared by both client and server (e.g., utilities).
    * **`:client`**: Client-only infrastructure (e.g., Design System, local DB drivers).
    * **`:server`**: Server-only infrastructure (e.g., Ktor plugins, and `:platform:server:postgres`, which owns the Flyway migrations and applies the Postgres codegen — see [server data](serverdata.md)).

##### Rules

* A `:feature` module must never depend on an `:app` module
* A `:feature:[name]:client` module must never depend on another `:client`/`:server` module
    * **Why:** A feature's client may only reach other features through their `:api` contract, or `:platform`.
* A `:feature:[name]:client` module may depend on any `:feature:[name]:api` module
    * **Enforced by:** `ModuleRules.clientApiOnly`
* A `:feature:[name]:server` module must never depend on another `:client`/`:server` module
    * **Why:** A feature's server may only reach other features through their `:api` contract, or `:platform`.
* A `:feature:[name]:server` module may depend on any `:feature:[name]:api` module
    * **Enforced by:** `ModuleRules.serverApiOnly`
* Code in one `feature.[name]` namespace must only depend on another feature's code that is declared in an `:api` module
    * **Why:** Several features may share one module (the `:feature:core` starting pattern), where the module-graph rules can't see the dependencies between them. Keeping cross-feature imports on `:api`-declared code keeps every feature liftable into its own module at any time.
    * **Note:** Between modules this is already enforced by `ModuleRules.clientApiOnly` and `ModuleRules.serverApiOnly`; this Rule adds the same guarantee within a module that hosts several feature namespaces.
    * **Note:** Imports that don't resolve to project source, such as KSP-generated bindings, are not tested.
    * **Note:** The one sanctioned cross-feature namespace is shared UI: `feature.common.client.ui` holds composite Compose components several features render, which can't live in Compose-free `:api`. It is UI-only — nothing outside `..common.client.ui..` is shareable this way. That carve-out answers a cross-*feature* question and is not the subsystem question: a subsystem package groups one feature's own code and is never shared at all (`ModuleRules.subsystemsNotPublished`).
* A file in a `:client` module must declare a `client` package, a file in a `:server` module a `server` package, and an `:api` module may declare either
    * **Why:** A declaration's package says what it is; the module it lives in says who may see it. When the two agree, the path gives the visibility and the package gives the layer, and publishing a type is moving one file rather than renaming it everywhere. When they disagree neither reading holds: a package with no side segment could be client or server code, so the module-graph rules and the package rules stop describing the same boundary.
    * **Note:** The feature root — `feature.[name]`, two segments — is allowed in every module: it is the shared vocabulary in `:api` and the feature's DI module in `:client`/`:server`.
    * **Note:** `:api` may declare both sides, because publishing a client or server type is what the module is for.
    * **Note:** `platform.**` packages inside a feature module are platform code that has not been lifted into its own module yet; the platform rules govern them, so they are out of scope here.
* A declaration in a layer's subsystem package must reside in a `:client` or `:server` module
    * **Why:** Publishing is moving a file between modules without changing its package, so a published subsystem declaration would put `feature.shop.client.domain.checkout` in `:api` and make another feature's compiler aware of one feature's internal decomposition. A subsystem exists because nothing outside the feature depends on it.  When another feature does need what a subsystem computes, the capability is restated as a layer-root contract that the subsystem satisfies. That costs one declaration and makes publication the visible act `:api` placement is meant to be.
    * **Note:** The layer root is publishable (`ServerDomain.publishedInterfacesInApi`): `feature.[name].[client|server].[layer]` in `:api` is the channel. Only the sub-packages below it are confined.
* A fully-qualified name under `feature.` must be declared in exactly one Gradle module
    * **Why:** The same name declared in two modules is a split package: which one a consumer sees depends on classpath order, so an import can resolve to different code in different builds, and a change to one copy silently does nothing at the other's call sites. Moving a type between `:api`, `:client`, and `:server` has to be a move, never a copy.
    * **Note:** Compared across modules only: a multiplatform `expect`/`actual` pair declares one name across several source sets of a single module, which is one declaration, not two.
    * **Note:** Tested over classes, interfaces, and objects — the shapes a consumer imports by name.
* A class in an `:api` module's `client.domain`/`server.domain` package must not implement a domain interface
    * **Why:** Publishing a file to `:api` shares a capability contract, never how it is satisfied. A class in `:api` that implements a domain interface would ship the implementation across the same channel as the interface, which is exactly what the `client.domain` / `server.domain` purity rules and the `:api` publication channel (D27) are there to prevent — the channel carries interfaces and models only.
    * **Note:** Tested against the client- and server-side Domain Interface Constructs, so a supertype counts only when it is both shaped like one — a `fun interface` with an `operator fun invoke` — and declared in a `client.domain`/`server.domain` package.
    * **Note:** A sealed interface is never a `fun interface`, so a sealed variant implementing its own nested sealed parent (e.g. `UpdateCampaign.Update`'s data classes) is not affected by this rule.
* The feature `:api` dependency graph must be acyclic
    * **Why:** When features graduate from a shared module (the `:feature:core` starting pattern) into their own `:feature:[name]` modules, every cross-feature `:api` import becomes a real `:feature:X:api` → `:feature:Y:api` Gradle dependency, and Gradle rejects circular project dependencies. Features caught in an `:api` cycle can never be housed in separate modules — they must graduate as one lump. Keeping the graph acyclic keeps every feature independently liftable.
    * **Note:** Only `:api` → `:api` edges can close a Gradle cycle: `:client`/`:server` code depends on other features' `:api` but never the reverse, so those edges can't form a ring. This Rule inspects only imports in `:api` sources that resolve to another feature's `:api` code.
    * **Note:** Cross-feature imports that resolve outside `:api` are reported by `ModuleRules.crossFeatureCodeViaApi`, not here.
    * **Note:** To keep a deliberate edge, annotate the `:api` source file holding the import with `@file:ArchitectureException(ruleIds = ["ModuleRules.apiGraphAcyclic"], reason = "…")`; its edges are then excluded from the graph.
* A `:platform` module must never depend on an `:app` module
* A `:platform` module must never depend on a `:feature` module

##### Guidance

* A `:feature` module may depend on `:platform` modules
* A `:feature:[name]:api` module may depend on another feature's `:api` module to share models
    * **Note:** `:api` to `:api` dependencies are allowed, but should be kept to a minimum.
    * **Note:** This audit reads the module graph, so it sees only features already housed in separate modules. `ModuleRules.apiMayUseApiSameModule` reports the same dependencies between features staged in one shared module.
    * **Audited:** a test reports non-conforming code without ever failing.
* Within a shared module, a feature's `:api` code may depend on another feature's `:api` code, but such dependencies should be kept minimal
    * **Note:** The staged-module counterpart to `ModuleRules.apiMayUseApi`: while several features share one module (the `:feature:core` pattern), their cross-feature `:api` dependencies are imports, not module-graph edges, so that audit can't see them. Each import reported here becomes a real `:feature:X:api` → `:feature:Y:api` edge when the features graduate, and every such edge constrains `ModuleRules.apiGraphAcyclic`.
    * **Note:** Only same-module dependencies are reported; once two features are housed separately, `ModuleRules.apiMayUseApi` takes over.
    * **Audited:** a test reports non-conforming code without ever failing.
* A `:feature` module may be grouped (`:feature:[group]:[name]:…`)
    * **Note:** A module that serves as a group should exist only as a group, and should not itself contain `:api`, `:server` or `:client` modules.
    * **Audited:** a test reports non-conforming code without ever failing.
* A `:platform` module may depend on other `:platform` modules
    * **Note:** `:platform` to `:platform` dependencies are allowed, but should be kept to a minimum.
    * **Audited:** a test reports non-conforming code without ever failing.
