package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider

@Describe("""
    The `services` axis defines the contract between client and server. The contract lives in
    `:api`, so both sides see it; the server-side implementation lives in `:server` under the same
    package name. The axis covers both the `:api` Service contract and the entire `:server`
    implementation surface: ServiceImpls, internal helpers and orchestrators, and Postgres storage.
    All of it lives under the one `feature..services..` package tree, so the axis is a single
    RuleGroup whose Constructs' package requirements keep the sub-axes (`internal`, `storage`,
    `tools`) disjoint.

    `services` is not a UI-equivalent outer layer. It sits parallel to the `data` axis and is
    consumed by it. On the client, [Repositories](data.md#repository) (in `data`) inject Service
    contracts to call the server. On the server, `services` is where the request-handling
    implementation lives, reaching into `services.storage` for persistence and
    `services.internal.*` for sub-tasks.

    Client/server communication uses **urpc** (`dev.isaacudy.udytils:urpc-*`): a service is an
    `@Urpc` interface, and KSP generates the client, the server binding, and the wire descriptors.
    See [Service Interface](#service-interface).

    ## Cross-axis dependencies

    Within a feature, the cross-axis dependency rules are:

    * `domain` may not depend on any other axis. It is the deepest layer.
    * `services` may depend on `domain`.
    * `data` (client only) may depend on `domain` and on `services` contracts, so Repositories can
      call the server.
    * `ui` (client only) may depend on `domain` only. It must not depend on `data` or `services`
      directly; calling the server goes through Repositories, which expose
      [domain interfaces](domain.md#domain-interface) for the UI to consume.
    * No axis may depend on `ui`.
    * Inside `services`, the dependency direction is `internal → storage`. See
      [`services.storage`](#servicesstorage--postgres-persistence).

    Reading these as a directed graph:

    * On the client: `ui → domain ← services ← data` (and `data → domain`).
    * On the server: `domain ← services` (with `services` reaching internally into
      `services.storage` and `services.internal`).

    `services` is a sibling of `data`, not an outer shell above it: the contract that `data`
    consumes on the client and `services` itself implements on the server. Two layer-level rules
    enforce the axis's place in that graph: `ServicesLayer.mustNotDependOnData` and
    `ServicesLayer.crossFeatureViaApi`, in the [rules](#rules) below.

    ## `services.internal`

    Server-side coordinator and helper classes: the things that do the work the ServiceImpl
    orchestrates. The bare `services.internal` package holds the top-level orchestrators that
    compose multiple subsystems (such as a `SessionProcessingManager`), plus the shared-payload
    data types they pass between them. Each `services.internal.<subsystem>` package is isolated
    under [hierarchical visibility](#hierarchical-visibility-within-servicesinternal).

    The package is modelled by five Constructs: [coordinators](#internal-coordinator),
    [data carriers](#internal-data-carrier), [internal interfaces](#internal-interface),
    [internal exceptions](#internal-exception), and [object helpers](#internal-object-helper).
    Each requires its shape plus residence in `feature.[name].services.internal`.

    ### Hierarchical visibility within `services.internal`

    `ServicesLayer.internalHierarchicalVisibility` (in the [rules](#rules)) isolates each
    subsystem. Inside `feature.[name].services.internal.**`, an import is allowed only if it
    points to:

    * the **same package**, or
    * a **descendant** package, or
    * an **ancestor** package, **and only when the imported declaration is a pure data shape**.

    Lateral and cousin imports are forbidden outright. Ancestor imports of behaviour-bearing types
    (regular classes, regular interfaces, top-level functions, objects with member functions) are
    forbidden too: they would let a subsystem invoke its parent or use behaviour from a higher
    level, which re-introduces the cross-subsystem coupling the rule prevents.

    The exception for data shapes lets orchestrator-mediated composition work: a payload type that
    flows from one subsystem through the orchestrator into another can live at a common ancestor
    (typically bare `services.internal`), and both subsystems may name it without invoking any
    behaviour.

    A "data shape" is any of:

    * `data class`, `enum class`, `value class`, `data object`,
    * `sealed class` / `sealed interface`,
    * an `object` that holds only `val` constants (no functions).

    A subsystem may subdivide into deeper subpackages. The rule applies recursively, so each new
    subpackage inherits the same isolation.

    ## `services.storage` — Postgres persistence

    > **ukpt status:** the Postgres toolkit lives in the `embedded-udytils` submodule
    > (`:postgres-core/koin/codegen/gradle-plugin/embedded`), so these rules are the documented
    > persistence standard. The `:platform:server:postgres` module that applies the codegen plugin
    > and owns the Flyway migrations is **created when the first server feature needs
    > persistence**; until then the `services.storage` rules pass vacuously (no storage code
    > exists yet).

    * **Definition:** A feature's persistence storage classes and mappings, built on
      **[Exposed](https://github.com/JetBrains/Exposed)** and the **`dev.isaacudy.udytils.postgres`**
      runtime. That runtime (in the `embedded-udytils` submodule, re-exported by
      `:platform:server:postgres` via `api(libs.udytils.postgres.core)`) provides `PostgresConfig`,
      `PostgresMigrator`, `PgNotificationBus`, and the custom Exposed column types
      (`JsonbColumnType`, `JsonColumnType`, `TextArrayColumnType`, `TimestampColumnType`). Do not
      re-implement these in feature code; extend the library instead.
    * **Contents (hand-written, in the feature):** [Storage classes](#storage-class)
      (`[Name]Storage`), [storage records](#storage-record),
      [mapping functions](#mapping-function) (conventionally collected in `[Name]Mappers.kt`), and
      [codec objects](#codec-object).
    * **Contents (generated, not in the feature):** the Exposed `Table` objects and `XxxRow` data
      classes are generated into the **shared `platform.server.postgres.tables` package**
      (`:platform:server:postgres`) and imported by each feature's storage code. See
      [generated `Table`/`Row` sources](#generated-tablerow-sources) and
      [the codegen pipeline](#postgres-codegen-pipeline--runtime).

    Storage sits at the bottom of the `services` axis: the dependency direction is
    `internal → storage`, never the reverse (`ServicesLayer.storageMustNotDependOnInternal`, in the
    [rules](#rules)).

    ### Generated `Table`/`Row` sources

    > All `Table`/`Row` codegen rules (`ServicesLayer.generatedTableRowSources` through
    > `ServicesLayer.rowFakeConstructorAndSetFromRow` in the [rules](#rules)) are guaranteed by the
    > `dev.isaacudy.udytils.postgres` plugin, not by tests: the generated sources live under
    > `build/generated/` and are never scanned. They live in the shared
    > `platform.server.postgres.tables` package, not in any feature's `services.storage`, so they
    > are declared as RuleGroup-level codegen rules rather than feature Constructs.

    * **Note:** The plugin registers two tasks: `generatePostgresTables` (the Exposed sources) and
      `exportPostgresSchema` (the committed `schema.sql` snapshot). Generated files live under
      `build/generated/source/postgres-tables/`, carry a
      `Generated by the dev.isaacudy.udytils.postgres Gradle plugin` header, and are not committed.

    A Storage class reads via the generated fake-constructor and writes via the `setFromRow`
    extension; see the layer examples after the [rules](#rules).

    ### Postgres codegen pipeline & runtime

    The persistence stack is built on the **`dev.isaacudy.udytils.postgres`** library (developed in
    the `embedded-udytils` submodule) plus **Exposed**, **Flyway**, and a **Zonky** embedded
    Postgres:

    * **Schema:** lives only in `:platform:server:postgres/src/main/resources/db/migration/` as
      Flyway scripts: versioned `V<n>__snake_name.sql` (run once, in order) and repeatable
      `R__name.sql` (re-run whenever their checksum changes, such as `R__notify_triggers.sql`). A
      schema change is a **new** `V<n>` file; existing `V<n>` files are never edited in place.
    * **Codegen:** `exportPostgresSchema` Flyway-migrates a throwaway Zonky Postgres and writes a
      normalised `schema.sql` snapshot; `generatePostgresTables` then emits the Exposed
      `Table`/`Row` sources from it into `platform.server.postgres.tables`. Both tasks are
      registered by the `dev.isaacudy.udytils.postgres` Gradle plugin and run before
      `compileKotlin`.
    * **Runtime ownership:** the DB primitives (`PostgresConfig`, `PostgresMigrator`,
      `PgNotificationBus`, the column types) live in the udytils library;
      `:platform:server:postgres` owns only the SQL migrations and codegen wiring and re-exports
      the runtime; the application (`:app:server`) owns its connection config
      (`ukptPostgresConfigFromEnv()`), wires `postgresDependencies(config)` (from
      `dev.isaacudy.udytils.postgres.koin`), and runs `PostgresMigrator.migrate()` before it
      starts serving.

    ### Reactive storage flows (`PgNotificationBus`)

    A `[Name]Storage` class may expose `Flow` reads that re-query when a Postgres `NOTIFY` fires,
    by injecting `dev.isaacudy.udytils.postgres.PgNotificationBus`. The channel name is a
    `companion object const val CHANNEL` and must match a `pg_notify(...)` trigger in the
    migrations (such as `R__notify_triggers.sql`). The shape is: emit an initial query, then
    `bus.listen(CHANNEL).filter { it == key }.collect { emit(query()) }`. This is convention, not
    a tested rule.

    ## `services.tools` (reserved)

    Reserved for AI tool-use subclasses, such as `AssistantTool` wrappers around a service. ukpt
    has no AI subsystem, so `services.tools` is intentionally empty: it defines no Construct, and
    any declaration placed here fails the exhaustiveness test until one is defined. Its isolation
    is enforced now, even though the package is empty: see `ServicesLayer.toolsApiContractOnly` in
    the [rules](#rules).

    * **Note:** If an AI subsystem is added later, add an `assistantTool` Construct (extends
      `AssistantTool`, named `[Action][Entity]Tool`) to the `ServicesLayer` group to populate this
      layer.
""")
object ServicesLayer : RuleGroup(
    inPackage = "feature..services..",
    constructs = listOf(
        ServiceInterface,
        ServiceImpl,
        InternalCoordinator,
        InternalDataCarrier,
        InternalInterface,
        InternalException,
        InternalObjectHelper,
        StorageClass,
        StorageRecord,
        MappingFunction,
        CodecObject,
    ),
) {

    // §4.4.5 `services.tools` is intentionally empty (reserved for AI tool-use subclasses), so it
    // defines no construct: any declaration placed there fails the exhaustiveness check until an
    // `assistantTool` construct is reintroduced. Its isolation rule lives at the layer level below.

    // ---- §3.4.4 cross-axis dependency rules (layer-level — not tied to one construct) ---------
    @Describe("The `services` layer may depend on `domain` and on other features' `:api` `services` contracts; it must not depend on `data`")
    val mustNotDependOnData by rule {
        rationale(
            """
            The server has no `data` layer, and the client's `data` depends on `services`, not the
            other way around. Reaching into client-only `data.storage` (Keychain, SharedPreferences)
            from a services file would fail at runtime or break the client/server split.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.containsPackageSegment("services") == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { it.name.matches(Regex("""feature\.[^.]+\.data\.storage\..+""")) }
                }
                .map { Violation(it.path, "services file imports client-only `data.storage`") }
        }
    }

    @Describe("The `services` layer may depend on another feature's `services` only via that feature's `:api` module")
    val crossFeatureViaApi by rule {
        enforcedBy("ModuleRules.clientApiOnly", "ModuleRules.serverApiOnly", "ModuleRules.crossFeatureCodeViaApi")
    }

    @Describe("A class in `services.internal.<subsystem>.**` must not import from a different subsystem under `services.internal` (ancestor data-shape imports are allowed)")
    val internalHierarchicalVisibility by rule {
        rationale(
            """
            Each direct child of `services.internal` is isolated: a subsystem may use its own
            children freely, its ancestors only for shared data shapes, and its siblings never.
            Cross-subsystem composition belongs to the orchestrator at bare `services.internal`,
            with shared payloads defined at a common ancestor.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            val ownFeatureInternalRegex = Regex("""^feature\.([^.]+)\.services\.internal(?:\.(.+))?$""")
            val internalImportRegex = Regex("""^feature\.([^.]+)\.services\.internal(?:\.(.+))?\.[^.]+$""")

            // Index project declarations by fully-qualified name so we can resolve each violating
            // import to the actual class/object definition and inspect its modifiers.
            val declarationsByFqn: Map<String, KoBaseDeclaration> =
                scope.declarations()
                    .filterIsInstance<KoFullyQualifiedNameProvider>()
                    .mapNotNull { decl ->
                        val fqn = decl.fullyQualifiedName ?: return@mapNotNull null
                        fqn to (decl as KoBaseDeclaration)
                    }
                    .toMap()

            fun isDataShape(importName: String): Boolean {
                val target = declarationsByFqn[importName] ?: return false
                return when (target) {
                    is KoClassDeclaration ->
                        target.hasDataModifier ||
                            target.hasEnumModifier ||
                            target.hasValueModifier ||
                            target.hasSealedModifier
                    is KoInterfaceDeclaration ->
                        // Only sealed interfaces count — regular interfaces are behaviour contracts.
                        target.hasSealedModifier
                    is KoObjectDeclaration ->
                        // Allow `data object` and any object that's purely a constants holder.
                        target.hasDataModifier ||
                            (target.functions().isEmpty() && target.properties().all { it.isVal && !it.isMutable() })
                    else -> false
                }
            }

            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    val pkg = file.packagee?.name ?: return@flatMap emptyList<Violation>()
                    val ownMatch = ownFeatureInternalRegex.matchEntire(pkg) ?: return@flatMap emptyList<Violation>()
                    val ownFeature = ownMatch.groupValues[1]
                    val ownSubpath = ownMatch.groupValues[2] // "" for bare services.internal

                    file.imports
                        .filter { import ->
                            val importMatch = internalImportRegex.matchEntire(import.name)
                                ?: return@filter false
                            val importFeature = importMatch.groupValues[1]
                            if (importFeature != ownFeature) return@filter false
                            val importSubpath = importMatch.groupValues[2] // "" for bare services.internal

                            val isSameOrDescendant = ownSubpath.isEmpty() ||
                                importSubpath == ownSubpath ||
                                importSubpath.startsWith("$ownSubpath.")
                            if (isSameOrDescendant) return@filter false

                            // Ancestor: importSubpath empty (bare services.internal), or ownSubpath
                            // strictly extends importSubpath with a `.`.
                            val isAncestor = importSubpath.isEmpty() ||
                                ownSubpath.startsWith("$importSubpath.")
                            if (isAncestor) {
                                // Allowed iff the imported declaration is a data shape.
                                return@filter !isDataShape(import.name)
                            }
                            // Otherwise it's lateral / cousin — forbidden outright.
                            true
                        }
                        .map {
                            Violation(
                                file.path,
                                "services.internal file imports `${it.name}` across a subsystem boundary " +
                                    "(lateral/cousin, or a non-data ancestor) — forbidden by hierarchical visibility",
                            )
                        }
                }
        }
    }

    @Describe("A `services.storage` file must not import from `services.internal`: the dependency direction inside `services` is `internal → storage`")
    val storageMustNotDependOnInternal by rule {
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.matches(Regex("""feature\.[^.]+\.services\.storage(\..+)?""")) == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { it.name.matches(Regex("""feature\.[^.]+\.services\.internal\..+""")) }
                }
                .map { Violation(it.path, "services.storage file imports from services.internal") }
        }
    }

    @Describe("A declaration placed in `services.tools` may only depend on the Service contract via `:api`-defined types, never on `services.storage` or `services.internal`")
    val toolsApiContractOnly by rule {
        rationale(
            """
            Tools are AI-callable wrappers around the Service contract: they should consume the
            `:api` Service interface only, not reach into Postgres tables or internal orchestrators
            directly. The isolation rule is enforced now even though the package is empty.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.matches(Regex("""feature\.[^.]+\.services\.tools(\..+)?""")) == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import ->
                        import.name.matches(Regex("""feature\.[^.]+\.services\.storage\..+""")) ||
                            import.name.matches(Regex("""feature\.[^.]+\.services\.internal\..+"""))
                    }
                }
                .map { Violation(it.path, "services.tools file imports services.storage/internal — use the `:api` contract only") }
        }
    }

    // ---- §4.4.4.2–§4.4.4.3 Postgres codegen (generated into `platform.server.postgres.tables`) -
    // These describe sources generated by the `dev.isaacudy.udytils.postgres` Gradle plugin; they
    // live in a shared platform package, are never committed, and are not scanned by Konsist — so
    // they are layer-level `codegen` rules, not feature constructs.
    @Describe("A `Table`/`Row` source is generated by the `dev.isaacudy.udytils.postgres` plugin from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables`")
    val generatedTableRowSources by rule { codegen() }
    @Describe("A persisted entity has a generated `object XxxTable : Table(\"xxx\")` (plural); custom columns use the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …)")
    val generatedTableObjects by rule { codegen() }
    @Describe("A generated `Table` object declares every column on the SQL table, with no omissions; the UUID primary key is `uuid(\"id\").autoGenerate()` but the write path always supplies the id explicitly")
    val everyColumnOnTable by rule { codegen() }
    @Describe("The in-memory persistence shape is a top-level `data class XxxRow` (singular) whose fields use only primitive types — no domain wrappers, enums, or sealed hierarchies")
    val rowDataClassPrimitives by rule { codegen() }
    @Describe("A generated file exposes a fake-constructor `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes")
    val rowFakeConstructorAndSetFromRow by rule { codegen() }
}

/**
 * The dotted package sub-path after `…services` for this declaration, or `null` if it isn't in a
 * `services` package. `""` for bare `feature.[name].services`; `"internal.foo"` / `"storage"` etc.
 * for the sub-axes. Guards against false matches like `…servicesRegistry`.
 */
private fun KoBaseDeclaration.servicesSubpath(): String? {
    val pkg = containingFilePackage()
    val idx = pkg.indexOf(".services")
    if (idx < 0) return null
    val after = pkg.substring(idx + ".services".length)
    if (after.isNotEmpty() && !after.startsWith(".")) return null
    return after.removePrefix(".")
}

private fun String.isUnderSegment(segment: String): Boolean = this == segment || startsWith("$segment.")

/** In the top-level `feature.[name].services` package (the contract / ServiceImpl), not a sub-axis. */
internal fun KoBaseDeclaration.isInServicesRoot(): Boolean {
    val sub = servicesSubpath() ?: return false
    return !sub.isUnderSegment("internal") && !sub.isUnderSegment("storage") && !sub.isUnderSegment("tools")
}

/** In the named `services` sub-axis (`internal`, `storage`, or `tools`) of any feature. */
internal fun KoBaseDeclaration.isInServicesSubAxis(segment: String): Boolean =
    servicesSubpath()?.isUnderSegment(segment) == true
