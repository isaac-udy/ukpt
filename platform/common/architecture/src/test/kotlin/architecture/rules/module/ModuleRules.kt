package architecture.rules.module

import architecture.registry.*


@Describe("""
    The project is organized into three root-level module groups — `:app`, `:feature`, and
    `:platform`. The dependency rules between them are **module-graph rules**: they are checked
    against the module dependency graph parsed from the `build.gradle.kts` files, not against Kotlin
    source. Build-file exemptions use the `// architecture-exception:` comment (see
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
""")
object ModuleRules : RuleGroup() {

    @Describe("A `:feature` module must never depend on an `:app` module")
    val featureNotApp by rule {
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { isApp(it.to) && isFeature(it.from) && !exempt(it) }
                .map { Violation(it.location, "forbidden :feature → :app dependency") }
        }
    }

    @Describe("A `:feature` module may depend on `:platform` modules")
    val featureMayUsePlatform by guidance

    @Describe("A `:feature:[name]:client` module must never depend on another `:client`/`:server` module")
    val clientApiOnly by rule {
        rationale("A feature's client may only reach other features through their `:api` contract, or `:platform`.")
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { featureSubmoduleType(it.from) == "client" }
                .filter { isFeature(it.to) && featureSubmoduleType(it.to) != "api" && !exempt(it) }
                .map { Violation(it.location, "feature :client may only depend on :api or :platform") }
        }
    }

    @Describe("A `:feature:[name]:client` module may depend on any `:feature:[name]:api` module")
    val clientMayUseApi by rule {
        enforcedBy(clientApiOnly)
    }

    @Describe("A `:feature:[name]:server` module must never depend on another `:client`/`:server` module")
    val serverApiOnly by rule {
        rationale("A feature's server may only reach other features through their `:api` contract, or `:platform`.")
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { featureSubmoduleType(it.from) == "server" }
                .filter { isFeature(it.to) && featureSubmoduleType(it.to) != "api" && !exempt(it) }
                .map { Violation(it.location, "feature :server may only depend on :api or :platform") }
        }
    }

    @Describe("A `:feature:[name]:server` module may depend on any `:feature:[name]:api` module")
    val serverMayUseApi by rule {
        enforcedBy(serverApiOnly)
    }

    @Describe("A `:feature:[name]:api` module may depend on another feature's `:api` module to share models")
    val apiMayUseApi by guidance {
        note("`:api` to `:api` dependencies are allowed, but should be used sparingly, treated with caution, and minimised where possible.")
        auditModuleGraph { graph, exempt ->
            graph.edges
                .filter { featureSubmoduleType(it.from) == "api" && featureSubmoduleType(it.to) == "api" && !exempt(it) }
                .map { Violation(it.location, "cross-feature :api → :api dependency — allowed, but keep these minimal") }
        }
    }

    @Describe("A `:feature` module may be grouped (`:feature:[group]:[name]:…`)")
    val featuresMayBeGrouped by guidance {
        note("A module that serves as a group should exist only as a group, and should not itself contain `:api`, `:server` or `:client` modules.")
    }

    @Describe("A `:platform` module must never depend on an `:app` module")
    val platformNotApp by rule {
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { isPlatform(it.from) && isApp(it.to) && !exempt(it) }
                .map { Violation(it.location, "forbidden :platform → :app dependency") }
        }
    }

    @Describe("A `:platform` module must never depend on a `:feature` module")
    val platformNotFeature by rule {
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { isPlatform(it.from) && isFeature(it.to) && !exempt(it) }
                .map { Violation(it.location, "forbidden :platform → :feature dependency") }
        }
    }

    @Describe("A `:platform` module may depend on other `:platform` modules")
    val platformMayUsePlatform by guidance {
        note("`:platform` to `:platform` dependencies are allowed, but should be used sparingly, treated with caution, and minimised where possible.")
    }
}

private fun isApp(path: String) = path.startsWith(":app:") || path == ":app"
private fun isFeature(path: String) = path.startsWith(":feature:") || path == ":feature"
private fun isPlatform(path: String) = path.startsWith(":platform:") || path == ":platform"

/** `:feature:core:server` → "server"; null if [path] isn't a `:feature:…:{api,client,server}` module. */
private fun featureSubmoduleType(path: String): String? {
    if (!isFeature(path)) return null
    return path.removePrefix(":").split(":").lastOrNull()?.takeIf { it in setOf("api", "client", "server") }
}
