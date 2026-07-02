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
    * **Client structure (AGP 9.0)**: Under AGP 9.0 a single Kotlin Multiplatform module can no longer also be a `com.android.application`, so the client is itself a group: a shared KMP library `:app:client:shared` (the `com.android.kotlin.multiplatform.library` plugin) holding the shared UI, navigation, DI wiring, and the iOS framework entry point (`iosMain`), plus thin per-platform application modules `:app:client:android` (`com.android.application`), `:app:client:desktop` (Compose Desktop), and `:app:client:web` (wasmJs). The per-platform modules contain only their entry point + platform packaging and depend on `:app:client:shared`.
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
      * If you are following this pattern, the named feature packages within `:feature:core` should only depend on other named packages via the api module
      * Example: If `:feature:core` contains `feature.auth` and `feature.invoices`, code in `feature.auth` should only depend on `feature.invoices` code which is in the `:feature:core:api` module
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
