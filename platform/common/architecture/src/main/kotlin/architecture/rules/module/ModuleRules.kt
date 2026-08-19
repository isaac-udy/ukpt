package architecture.rules.module

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containingFilePath
import architecture.definitions.featureLayerPath
import architecture.definitions.featureName
import architecture.definitions.isApiModule
import architecture.definitions.isClientModule
import architecture.definitions.isFeatureModule
import architecture.definitions.isServerModule
import architecture.rules.clientdomain.DomainInterface as ClientDomainInterface
import architecture.rules.serverdomain.DomainInterface as ServerDomainInterface
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider


@Describe("""
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

    @Describe("Code in one `feature.[name]` namespace must only depend on another feature's code that is declared in an `:api` module")
    val crossFeatureCodeViaApi by rule {
        rationale(
            """
            Several features may share one module (the `:feature:core` starting pattern), where the
            module-graph rules can't see the dependencies between them. Keeping cross-feature
            imports on `:api`-declared code keeps every feature liftable into its own module at
            any time.
            """.trimIndent(),
        )
        note("Between modules this is already enforced by `ModuleRules.clientApiOnly` and `ModuleRules.serverApiOnly`; this Rule adds the same guarantee within a module that hosts several feature namespaces.")
        note("Imports that don't resolve to project source, such as KSP-generated bindings, are not tested.")
        note("The one sanctioned cross-feature namespace is shared UI: `feature.common.client.ui` holds composite Compose components several features render, which can't live in Compose-free `:api`. It is UI-only — nothing outside `..common.client.ui..` is shareable this way. That carve-out answers a cross-*feature* question and is not the subsystem question: a subsystem package groups one feature's own code and is never shared at all (`ModuleRules.subsystemsNotPublished`).")
        scope { scope, exempt ->
            // Index of project declarations by fully-qualified name, so imports resolve to the
            // module that declares them (nested types resolve via longest matching prefix).
            val declaredInApi: Map<String, Boolean> = scope.declarations(includeNested = true)
                .filterIsInstance<KoFullyQualifiedNameProvider>()
                .mapNotNull { decl ->
                    val fqn = decl.fullyQualifiedName ?: return@mapNotNull null
                    fqn to (decl as KoBaseDeclaration).isApiModule()
                }
                .toMap()

            fun resolvesOutsideApi(importName: String): Boolean {
                var candidate = importName
                while (candidate.contains('.')) {
                    declaredInApi[candidate]?.let { isApi -> return !isApi }
                    candidate = candidate.substringBeforeLast('.')
                }
                return false // unresolved (generated or external) — not tested
            }

            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    val ownFeature = file.featureName()
                    if (ownFeature.isBlank()) return@flatMap emptyList<Violation>()
                    file.imports
                        .filter { it.name.startsWith("feature.") }
                        .filter { it.featureName().isNotBlank() && it.featureName() != ownFeature }
                        // Shared-UI carve-out: `feature.common.client.ui` holds composite UI
                        // components (e.g. `EventCard`, `EntityRichTextField`) that several features
                        // render. A Compose composite can't live in `:api` (which is Compose-free and
                        // consumed by `:server`), so this is the one sanctioned cross-feature
                        // namespace. It is UI-only by design — scoped to `..common.client.ui..` so it
                        // can never leak non-UI coupling; everything else still shares through `:api`.
                        .filterNot { it.name.startsWith("feature.common.client.ui.") }
                        .filter { resolvesOutsideApi(it.name) }
                        .map { Violation(file.path, "cross-feature import `${it.name}` resolves outside an `:api` module") }
                }
        }
    }

    @Describe("A file in a `:client` module must declare a `client` package, a file in a `:server` module a `server` package, and an `:api` module may declare either")
    val sidePackageMatchesModule by rule {
        rationale(
            """
            A declaration's package says what it is; the module it lives in says who may see it. When
            the two agree, the path gives the visibility and the package gives the layer, and
            publishing a type is moving one file rather than renaming it everywhere. When they
            disagree neither reading holds: a package with no side segment could be client or server
            code, so the module-graph rules and the package rules stop describing the same boundary.
            """.trimIndent(),
        )
        note("The feature root — `feature.[name]`, two segments — is allowed in every module: it is the shared vocabulary in `:api` and the feature's DI module in `:client`/`:server`.")
        note("`:api` may declare both sides, because publishing a client or server type is what the module is for.")
        note("`platform.**` packages inside a feature module are platform code that has not been lifted into its own module yet; the platform rules govern them, so they are out of scope here.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .mapNotNull { file ->
                    val pkg = file.packagee?.name.orEmpty()
                    if (!pkg.startsWith("feature.")) return@mapNotNull null
                    val withinFeature = pkg.removePrefix("feature.").substringAfter('.', missingDelimiterValue = "")
                    if (withinFeature.isEmpty()) return@mapNotNull null // the feature root, allowed everywhere
                    val declaredSide = withinFeature.substringBefore('.')
                    val (module, allowedSides) = when {
                        file.isClientModule() -> ":client" to setOf("client")
                        file.isServerModule() -> ":server" to setOf("server")
                        file.isApiModule() -> ":api" to setOf("client", "server")
                        else -> return@mapNotNull null
                    }
                    if (declaredSide in allowedSides) return@mapNotNull null
                    Violation(
                        file.path,
                        "`$pkg` is declared in a $module module, which may only declare " +
                            allowedSides.joinToString(" or ") { "`feature.[name].$it.**`" } +
                            " (or the feature root)",
                    )
                }
        }
    }

    @Describe("A declaration in a layer's subsystem package must reside in a `:client` or `:server` module")
    val subsystemsNotPublished by rule {
        rationale(
            """
            Publishing is moving a file between modules without changing its package, so a published
            subsystem declaration would put `…domain.processing.audio` in `:api` and make another
            feature's compiler aware of one feature's internal decomposition. A subsystem exists
            precisely because nobody outside the feature has an opinion about it.

            When another feature does need what a subsystem computes, the capability is restated as a
            layer-root contract that the subsystem satisfies. That costs one declaration and makes
            publication the visible act `:api` placement is meant to be.
            """.trimIndent(),
        )
        note("The layer root is publishable, as it always has been (`ServerDomain.publishedInterfacesInApi`): `feature.[name].[side].[layer]` in `:api` is the channel. Only the sub-packages below it are confined.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isApiModule() }
                .filterNot { exempt(it) }
                .mapNotNull { file ->
                    val pkg = file.packagee?.name.orEmpty()
                    val path = featureLayerPath(pkg) ?: return@mapNotNull null
                    if (path.subsystem.isEmpty()) return@mapNotNull null
                    Violation(
                        file.path,
                        "`$pkg` is a subsystem package declared in an `:api` module — publish a " +
                            "`${path.side}.${path.layer}` root contract the subsystem satisfies instead",
                    )
                }
        }
    }

    @Describe("A fully-qualified name under `feature.` must be declared in exactly one Gradle module")
    val noDuplicateFqnAcrossTrio by rule {
        rationale(
            """
            The same name declared in two modules is a split package: which one a consumer sees
            depends on classpath order, so an import can resolve to different code in different
            builds, and a change to one copy silently does nothing at the other's call sites. Moving
            a type between `:api`, `:client`, and `:server` has to be a move, never a copy.
            """.trimIndent(),
        )
        note("Compared across modules only: a multiplatform `expect`/`actual` pair declares one name across several source sets of a single module, which is one declaration, not two.")
        note("Tested over classes, interfaces, and objects — the shapes a consumer imports by name.")
        scope { scope, exempt ->
            val modulesByFqn = mutableMapOf<String, MutableMap<String, String>>()
            scope.classesAndInterfacesAndObjects(includeNested = false)
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .forEach { declaration ->
                    val fqn = declaration.fullyQualifiedName ?: return@forEach
                    if (!fqn.startsWith("feature.")) return@forEach
                    val module = declaration.containingFile.path.substringBeforeLast("/src/")
                    modulesByFqn.getOrPut(fqn) { mutableMapOf() }
                        .putIfAbsent(module, declaration.containingFile.path)
                }
            modulesByFqn
                .filterValues { it.size > 1 }
                .flatMap { (fqn, sites) ->
                    sites.values.map { path ->
                        Violation(path, "`$fqn` is declared in ${sites.size} modules — one name, one module, or the classpath decides which one a consumer gets")
                    }
                }
        }
    }

    @Describe("A class in an `:api` module's `client.domain`/`server.domain` package must not implement a domain interface")
    val noDomainImplementationsInApi by rule {
        rationale(
            """
            Publishing a file to `:api` shares a capability contract, never how it is satisfied.
            A class in `:api` that implements a domain interface would ship the implementation
            across the same channel as the interface, which is exactly what the `client.domain` /
            `server.domain` purity rules and the `:api` publication channel (D27) are there to
            prevent — the channel carries interfaces and models only.
            """.trimIndent(),
        )
        note("Tested against the client- and server-side Domain Interface Constructs, so a supertype counts only when it is both shaped like one — a `fun interface` with an `operator fun invoke` — and declared in a `client.domain`/`server.domain` package.")
        note("A sealed interface is never a `fun interface`, so a sealed variant implementing its own nested sealed parent (e.g. `UpdateCampaign.Update`'s data classes) is not affected by this rule.")
        scope { scope, exempt ->
            scope.classes()
                .filter { it.isFeatureModule() && it.isApiModule() }
                .filter { cls ->
                    val pkg = cls.containingFilePackage()
                    pkg.contains(".client.domain") || pkg.contains(".server.domain")
                }
                .filterNot { exempt(it) }
                .filter { cls ->
                    cls.parents().any { parent ->
                        val source = parent.sourceDeclaration as? KoBaseDeclaration
                        ClientDomainInterface.test(source) || ServerDomainInterface.test(source)
                    }
                }
                .map { Violation(it, "class in an `:api` module implements a domain interface — only the interface may be published, never its implementation") }
        }
    }

    @Describe("The feature `:api` dependency graph must be acyclic")
    val apiGraphAcyclic by rule {
        rationale(
            """
            When features graduate from a shared module (the `:feature:core` starting pattern) into
            their own `:feature:[name]` modules, every cross-feature `:api` import becomes a real
            `:feature:X:api` → `:feature:Y:api` Gradle dependency, and Gradle rejects circular
            project dependencies. Features caught in an `:api` cycle can never be housed in separate
            modules — they must graduate as one lump. Keeping the graph acyclic keeps every feature
            independently liftable.
            """.trimIndent(),
        )
        note("Only `:api` → `:api` edges can close a Gradle cycle: `:client`/`:server` code depends on other features' `:api` but never the reverse, so those edges can't form a ring. This Rule inspects only imports in `:api` sources that resolve to another feature's `:api` code.")
        note("Cross-feature imports that resolve outside `:api` are reported by `ModuleRules.crossFeatureCodeViaApi`, not here.")
        note("To keep a deliberate edge, annotate the `:api` source file holding the import with `@file:ArchitectureException(ruleIds = [\"ModuleRules.apiGraphAcyclic\"], reason = \"…\")`; its edges are then excluded from the graph.")
        scope { scope, exempt ->
            // Index of project declarations by fully-qualified name, so imports resolve to the
            // module that declares them (nested types resolve via longest matching prefix).
            val declaredInApi: Map<String, Boolean> = scope.declarations(includeNested = true)
                .filterIsInstance<KoFullyQualifiedNameProvider>()
                .mapNotNull { decl ->
                    val fqn = decl.fullyQualifiedName ?: return@mapNotNull null
                    fqn to (decl as KoBaseDeclaration).isApiModule()
                }
                .toMap()

            fun resolvesToApi(importName: String): Boolean {
                var candidate = importName
                while (candidate.contains('.')) {
                    declaredInApi[candidate]?.let { isApi -> return isApi }
                    candidate = candidate.substringBeforeLast('.')
                }
                return false // unresolved (generated or external), or resolves outside :api
            }

            // Feature → feature edges (X depends on Y), each with the import sites that create it.
            val edges = mutableMapOf<Pair<String, String>, MutableList<String>>()
            scope.files
                .filter { it.isApiModule() && it.isFeatureModule() }
                .filterNot { exempt(it) }
                .forEach { file ->
                    val from = file.featureName()
                    if (from.isBlank()) return@forEach
                    file.imports
                        .filter { it.name.startsWith("feature.") }
                        .filter { it.featureName().isNotBlank() && it.featureName() != from }
                        .filter { resolvesToApi(it.name) }
                        .forEach { import ->
                            edges.getOrPut(from to import.featureName()) { mutableListOf() } += file.path
                        }
                }

            val adjacency: Map<String, Set<String>> = edges.keys
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }
            val nodes = edges.keys.flatMap { listOf(it.first, it.second) }.toSet()

            stronglyConnectedComponents(nodes, adjacency)
                .filter { it.size > 1 }
                .sortedBy { component -> component.sorted().joinToString(",") }
                .map { component ->
                    val members = component.toSet()
                    val within = edges
                        .filterKeys { it.first in members && it.second in members }
                        .toSortedMap(compareBy({ it.first }, { it.second }))
                    val detail = within.entries.joinToString("") { (edge, sites) ->
                        "\n        ${edge.first} → ${edge.second} (${sites.size} import(s), e.g. ${sites.first()})"
                    }
                    Violation(
                        "feature :api cycle [${component.sorted().joinToString(", ")}]",
                        "these features form a cycle in the `:api` dependency graph and can't be housed " +
                            "in separate modules until it is broken; cut the thinnest edge:$detail",
                    )
                }
        }
    }

    @Describe("A `:feature:[name]:api` module may depend on another feature's `:api` module to share models")
    val apiMayUseApi by guidance {
        note("`:api` to `:api` dependencies are allowed, but should be kept to a minimum.")
        note("This audit reads the module graph, so it sees only features already housed in separate modules. `ModuleRules.apiMayUseApiSameModule` reports the same dependencies between features staged in one shared module.")
        auditModuleGraph { graph, exempt ->
            graph.edges
                .filter { featureSubmoduleType(it.from) == "api" && featureSubmoduleType(it.to) == "api" && !exempt(it) }
                .map { Violation(it.location, "cross-feature :api → :api dependency — allowed, but keep these minimal") }
        }
    }

    @Describe("Within a shared module, a feature's `:api` code may depend on another feature's `:api` code, but such dependencies should be kept minimal")
    val apiMayUseApiSameModule by guidance {
        note("The staged-module counterpart to `ModuleRules.apiMayUseApi`: while several features share one module (the `:feature:core` pattern), their cross-feature `:api` dependencies are imports, not module-graph edges, so that audit can't see them. Each import reported here becomes a real `:feature:X:api` → `:feature:Y:api` edge when the features graduate, and every such edge constrains `ModuleRules.apiGraphAcyclic`.")
        note("Only same-module dependencies are reported; once two features are housed separately, `ModuleRules.apiMayUseApi` takes over.")
        audit { scope, exempt ->
            // Index of project declarations by fully-qualified name to the module that declares them,
            // so an import resolves to its module (nested types resolve via longest matching prefix).
            val declaringModule: Map<String, String> = scope.declarations(includeNested = true)
                .filterIsInstance<KoFullyQualifiedNameProvider>()
                .mapNotNull { decl ->
                    val fqn = decl.fullyQualifiedName ?: return@mapNotNull null
                    fqn to (decl as KoBaseDeclaration).containingFilePath().substringBeforeLast("/src/")
                }
                .toMap()

            fun resolveModule(importName: String): String? {
                var candidate = importName
                while (candidate.contains('.')) {
                    declaringModule[candidate]?.let { return it }
                    candidate = candidate.substringBeforeLast('.')
                }
                return null
            }

            // Same-module cross-feature :api → :api imports, counted per feature pair.
            val pairCounts = mutableMapOf<Pair<String, String>, Int>()
            scope.files
                .filter { it.isApiModule() && it.isFeatureModule() }
                .filterNot { exempt(it) }
                .forEach { file ->
                    val from = file.featureName()
                    if (from.isBlank()) return@forEach
                    val sourceModule = file.path.substringBeforeLast("/src/")
                    file.imports
                        .filter { it.name.startsWith("feature.") }
                        .filter { it.featureName().isNotBlank() && it.featureName() != from }
                        .filter { resolveModule(it.name) == sourceModule }
                        .forEach { import ->
                            val key = from to import.featureName()
                            pairCounts[key] = (pairCounts[key] ?: 0) + 1
                        }
                }

            pairCounts
                .toSortedMap(compareBy({ it.first }, { it.second }))
                .map { (pair, count) ->
                    Violation(
                        "feature :api ${pair.first} → ${pair.second}",
                        "same-module cross-feature :api dependency ($count import(s)) — allowed, but keep these minimal; becomes a :feature:${pair.first}:api → :feature:${pair.second}:api edge on graduation",
                    )
                }
        }
    }

    @Describe("A `:feature` module may be grouped (`:feature:[group]:[name]:…`)")
    val featuresMayBeGrouped by guidance {
        note("A module that serves as a group should exist only as a group, and should not itself contain `:api`, `:server` or `:client` modules.")
        auditModuleGraph { graph, _ ->
            val featureParents = graph.edges
                .flatMap { listOf(it.from, it.to) }
                .filter { it.startsWith(":feature:") && it.substringAfterLast(':') in setOf("api", "client", "server") }
                .map { it.substringBeforeLast(':') }
                .toSet()
            featureParents
                .filter { parent -> featureParents.any { other -> other != parent && other.startsWith("$parent:") } }
                .map { Violation(it, "module is both a feature (direct :api/:client/:server) and a group (contains nested features) — groups should stay pure") }
        }
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
        note("`:platform` to `:platform` dependencies are allowed, but should be kept to a minimum.")
        auditModuleGraph { graph, exempt ->
            graph.edges
                .filter { isPlatform(it.from) && isPlatform(it.to) && !exempt(it) }
                .map { Violation(it.location, ":platform → :platform dependency — allowed, but keep these minimal") }
        }
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

/**
 * Tarjan's strongly-connected components over the feature graph. Returns every component; the
 * caller keeps those of size > 1 — a set of features that can all reach each other, i.e. a cycle.
 * Nodes and out-edges are visited in sorted order so the reported components are deterministic.
 */
private fun stronglyConnectedComponents(
    nodes: Set<String>,
    edges: Map<String, Set<String>>,
): List<List<String>> {
    var counter = 0
    val index = mutableMapOf<String, Int>()
    val low = mutableMapOf<String, Int>()
    val onStack = mutableSetOf<String>()
    val stack = ArrayDeque<String>()
    val components = mutableListOf<List<String>>()

    fun connect(v: String) {
        index[v] = counter
        low[v] = counter
        counter++
        stack.addLast(v)
        onStack += v
        edges[v].orEmpty().sorted().forEach { w ->
            when {
                w !in index -> {
                    connect(w)
                    low[v] = minOf(low.getValue(v), low.getValue(w))
                }
                w in onStack -> low[v] = minOf(low.getValue(v), index.getValue(w))
            }
        }
        if (low.getValue(v) == index.getValue(v)) {
            val component = mutableListOf<String>()
            do {
                val w = stack.removeLast()
                onStack -= w
                component += w
            } while (w != v)
            components += component
        }
    }

    nodes.sorted().forEach { if (it !in index) connect(it) }
    return components
}
