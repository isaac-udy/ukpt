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
    * **Client structure (AGP 9.0)**: Under AGP 9.0 a single Kotlin Multiplatform module can no longer also be a `com.android.application`, so the client is itself a group: a common KMP library `:app:client:common` (the `com.android.kotlin.multiplatform.library` plugin) holding the shared UI, navigation, DI wiring, and the iOS framework entry point (`iosMain`), plus thin per-platform application modules `:app:client:android` (`com.android.application`), `:app:client:desktop` (Compose Desktop), and `:app:client:web` (wasmJs). The per-platform modules contain only their entry point + platform packaging and depend on `:app:client:common`.
* **Constraints**: Must not contain business logic. Limited to infrastructure configuration and DI module aggregation.

## `:feature` (Vertical slices of functionality)

* **Purpose**: Encapsulated feature-specific functionality.
* **Sub-Modules**:
    * **`:api`**: Mandatory. Contains the shared contract.
    * **`:client`**: Optional. Contains UI and client-side logic.
    * **`:server`**: Optional. Contains server-side implementation.
* **Notes**:
    * Small projects may start with a single `:feature:core` containing all feature/domain code. As complexity increases, logic is migrated into specific `:feature:name` modules.
    * When starting with a single `:feature:core` feature module, it is a good idea to "preempt" the migration of `:feature:core` into individual `:feature:[name]` modules by using `feature.[name]` for package names within `:feature:core` (instead of `feature.core`)
      * The named feature packages within `:feature:core` must only depend on other named packages via the api module (enforced by `ModuleRules.crossFeatureCodeViaApi`), which keeps every feature liftable into its own module.
      * Example: If `:feature:core` contains `feature.auth` and `feature.invoices`, code in `feature.auth` may only depend on `feature.invoices` code which is in the `:feature:core:api` module
    * `:client` and `:server` modules are optional, but at least one of the two should exist for every feature.

## `:platform` (Infrastructure)

* **Purpose**: Reusable, non-feature-specific capabilities.
* **Sub-Groups**:
    * **`:common`**: Code shared by both client and server (e.g., utilities).
    * **`:client`**: Client-only infrastructure (e.g., Design System, local DB drivers).
    * **`:server`**: Server-only infrastructure (e.g., Ktor plugins, and `:platform:server:postgres` — which owns the Flyway SQL migrations + `schema.sql` and applies the `dev.isaacudy.udytils.postgres` codegen plugin; the DB runtime itself lives in that udytils library).

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
    * **Why:** Publishing is moving a file between modules without changing its package, so a published subsystem declaration would put `…domain.processing.audio` in `:api` and make another feature's compiler aware of one feature's internal decomposition. A subsystem exists precisely because nobody outside the feature has an opinion about it.  When another feature does need what a subsystem computes, the capability is restated as a layer-root contract that the subsystem satisfies. That costs one declaration and makes publication the visible act `:api` placement is meant to be.
    * **Note:** The layer root is publishable, as it always has been (`ServerDomain.publishedInterfacesInApi`): `feature.[name].[side].[layer]` in `:api` is the channel. Only the sub-packages below it are confined.
* A fully-qualified name under `feature.` must be declared in exactly one Gradle module
    * **Why:** The same name declared in two modules is a split package: which one a consumer sees depends on classpath order, so an import can resolve to different code in different builds, and a change to one copy silently does nothing at the other's call sites. Moving a type between `:api`, `:client`, and `:server` has to be a move, never a copy.
    * **Note:** Compared across modules only: a multiplatform `expect`/`actual` pair declares one name across several source sets of a single module, which is one declaration, not two.
    * **Note:** Tested over classes, interfaces, and objects — the shapes a consumer imports by name.
* A class in an `:api` module's `client.domain`/`server.domain` package must not implement a domain interface
    * **Why:** Publishing a file to `:api` shares a capability contract, never how it is satisfied. A class in `:api` that implements a domain interface would ship the implementation across the same channel as the interface, which is exactly what the `client.domain` / `server.domain` purity rules and the `:api` publication channel (D27) are there to prevent — the channel carries interfaces and models only.
    * **Note:** Tested against the client- and server-side Domain Interface Constructs, so a supertype counts only when it is both shaped like one — a `fun interface` with an `operator fun invoke` — and declared in a `client.domain`/`server.domain` package.
    * **Note:** A sealed interface is never a `fun interface`, so a sealed variant implementing its own nested sealed parent (e.g. `UpdateCampaign.Update`'s data classes) is not affected by this rule.
* A `:platform` module must never depend on an `:app` module
* A `:platform` module must never depend on a `:feature` module

##### Guidance

* A `:feature` module may depend on `:platform` modules
* A `:feature:[name]:api` module may depend on another feature's `:api` module to share models
    * **Note:** `:api` to `:api` dependencies are allowed, but should be kept to a minimum.
    * **Audited:** a test reports non-conforming code without ever failing.
* A `:feature` module may be grouped (`:feature:[group]:[name]:…`)
    * **Note:** A module that serves as a group should exist only as a group, and should not itself contain `:api`, `:server` or `:client` modules.
    * **Audited:** a test reports non-conforming code without ever failing.
* A `:platform` module may depend on other `:platform` modules
    * **Note:** `:platform` to `:platform` dependencies are allowed, but should be kept to a minimum.
    * **Audited:** a test reports non-conforming code without ever failing.
