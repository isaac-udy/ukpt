package architecture.rules.serverdata

import architecture.definitions.featureName
import architecture.definitions.isFeatureModule
import architecture.utils.isGeneratedTableImport
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    `feature.[name].server.data` — Postgres persistence and external integrations. The server's
    outer edge, and the mirror of [`client.data`](clientdata.md).

    The layer has two levels, and which one you are writing decides what you may name — the package
    says which one you are in:

    * At the `server.data` root, a [Repository](#repository) is the edge of
      [`server.domain`](serverdomain.md). It **provides**
      [server domain interfaces](serverdomain.md#domain-interface) by exposing them as `public val`
      properties rather than inheriting them, injects the StorageClasses it needs, and maps the Rows
      they return into domain objects. A domain object may span several tables; that composition
      happens here. This is the same construct as the client's
      [Repository](clientdata.md#repository), with the same rules — one pattern, learned once,
      appearing on both sides.
    * In the `server.data.storage` subpackage, a [StorageClass](#storage-class) speaks
      [Rows](#storage-record) only. It holds the queries and the single write path for the tables it
      owns, and it names no domain type at all: the mapping is the Repository's job, not the
      query's. `.storage` is the Row-only world, and it mirrors
      [`client.data.storage`](clientdata.md#client-storage) on the other side.

    [IntegrationClients](#integration-client) are the Repository idea pointed outward — an adapter
    onto GenAI, email, transcription, or object storage, providing a domain interface the server
    states in its own terms rather than the vendor's, with no table underneath. They sit at the
    `server.data` root, next to the Repositories they are the shape of. The vendor half of such a
    call — a model name, a prompt written for that model, a response schema — belongs beside the
    client that sends it, never in the layers above.

    [Storage records](#storage-record), [codec objects](#codec-object), and
    [mapping functions](#mapping-function) are the supporting shapes: the persistence types a
    StorageClass returns, the JSON encoders that put a domain shape in a column, and the
    `XxxRow.toDomain()` functions a Repository maps through. Storage records are Rows, so they live
    in `server.data.storage` beside the StorageClass that returns them; codecs and mapping functions
    name domain types by definition, so they belong at the `server.data` root.

    **This layer must never import `server.services`.** Wire contracts stay out of persistence, and
    storage can never reach the thing that is meant to consume it. That is one half of the hexagon;
    [`ServerServices.noDataImports`](serverservices.md#rules) is the other.

    ## Table ownership

    Ownership runs from the table, not from the class. **Every table has exactly one owning
    StorageClass** (`ServerData.tableOwnedBySingleStorage`), owned by exactly one feature
    (`ServerData.tableOwnedBySingleFeature`), and reachable only from this layer
    (`ServerData.tableAccessOwnedByStorage`). A StorageClass may own **several** tables, and should
    when they change together: two tables locked and incremented in one transaction have one set of
    invariants, and splitting them across two classes splits the invariants with them. What is
    forbidden is the other direction — a table with two owners has no single place its rules live,
    and a rule or a side effect one owner applies is one the other can skip.

    ## Transactions

    A [Repository](#repository) or a [UseCase](serverdomain.md#use-case) may inject
    `platform.server.postgres.TransactionRunner` and run several writes in one transaction
    (`ProjectRules.transactionRunnerInjectedByUseCaseOrRepository`). Storage calls made inside the
    block **join** that transaction, so a StorageClass never states whether it is in one: it issues
    the same query either way, and the caller that knows which writes have to land together decides
    the boundary. That is what lets a UseCase compose one feature's write with another's — see
    [server domain interfaces](serverdomain.md#domain-interface).

    ## Postgres persistence

    > The Postgres toolkit lives in the `embedded-udytils` submodule
    > (`:postgres-core/koin/codegen/gradle-plugin/embedded`), and `:platform:server:postgres`
    > applies the codegen plugin and owns the Flyway migrations, so these rules are the live
    > persistence standard for every feature that stores data.

    * **Definition:** A feature's persistence storage classes and mappings, built on
      **[Exposed](https://github.com/JetBrains/Exposed)** and the **`dev.isaacudy.udytils.postgres`**
      runtime. That runtime (in the `embedded-udytils` submodule, re-exported by
      `:platform:server:postgres` via `api(libs.udytils.postgres.core)`) provides `PostgresConfig`,
      `PostgresMigrator`, `PgNotificationBus`, and the custom Exposed column types
      (`JsonbColumnType`, `JsonColumnType`, `TextArrayColumnType`, `TimestampColumnType`). Do not
      re-implement these in feature code; extend the library instead.
    * **Contents (hand-written, in the feature):** [Repositories](serverdata.md#repository)
      (`[Name]Repository`),
      [mapping functions](serverdata.md#mapping-function) (conventionally collected in `[Name]Mappers.kt`), and
      [codec objects](serverdata.md#codec-object) at the `server.data` root;
      [Storage classes](serverdata.md#storage-class) (`[Name]Storage`) and
      [storage records](serverdata.md#storage-record) in `server.data.storage`.
    * **Contents (generated, not in the feature):** the Exposed `Table` objects and `XxxRow` data
      classes are generated into the **shared `platform.server.postgres.tables` package**
      (`:platform:server:postgres`) and imported by each feature's storage code. See
      [generated `Table`/`Row` sources](#generated-tablerow-sources) and
      [the codegen pipeline](#postgres-codegen-pipeline--runtime).

    Persistence is the outer edge of the server: `server.services` never imports it
    (`ServerServices.noDataImports`), and it never imports `server.services`
    (`ServerData.noServiceImports`).

    ### Generated `Table`/`Row` sources

    > All `Table`/`Row` codegen rules (`ServerData.generatedTableRowSources` through
    > `ServerData.rowFakeConstructorAndSetFromRow`, in the [rules](#rules) below) are guaranteed by
    > the `dev.isaacudy.udytils.postgres` plugin, not by tests: the generated sources live under
    > `build/generated/` and are never scanned. They live in the shared
    > `platform.server.postgres.tables` package, not in any feature's persistence package, so they
    > are declared as RuleGroup-level codegen rules rather than feature Constructs. Because that
    > package is shared, importing from it is what `ServerData.tableAccessOwnedByStorage` confines to
    > this layer.

    * **Note:** The plugin registers two tasks: `generatePostgresTables` (the Exposed sources) and
      `exportPostgresSchema` (the committed `schema.sql` snapshot). Generated files live under
      `build/generated/source/postgres-tables/`, carry a
      `Generated by the dev.isaacudy.udytils.postgres Gradle plugin` header, and are not committed.

    A Storage class reads via the generated fake-constructor and writes via the `setFromRow`
    extension.

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
      the runtime; the application (`:app:server`) owns its connection config (a
      `postgresConfigFromEnv()`-style factory), wires `postgresDependencies(config)` (from
      `dev.isaacudy.udytils.postgres.koin`), and runs `PostgresMigrator.migrate()` before it
      starts serving.

    ### Reactive storage flows (`PgNotificationBus`)

    A `[Name]Storage` class may expose `Flow` reads that re-query when a Postgres `NOTIFY` fires,
    by injecting `dev.isaacudy.udytils.postgres.PgNotificationBus`. The channel name is a
    `companion object const val CHANNEL` and must match a `pg_notify(...)` trigger in the
    migrations (such as `R__notify_triggers.sql`). The shape is: emit an initial query, then
    `bus.listen(CHANNEL).filter { it == key }.collect { emit(query()) }`. This is convention, not
    a tested rule.
""")
object ServerData : RuleGroup(
    inPackage = "feature..server.data..",
    constructs = listOf(
        Repository,
        StorageClass,
        StorageRecord,
        CodecObject,
        MappingFunction,
        IntegrationClient,
    ),
) {

    @Describe("The `server.data` layer must never import `server.services`")
    val noServiceImports by rule {
        rationale(
            """
            Persistence exists to satisfy the domain, not to serve requests. An import of a service
            contract would put the wire format inside the storage layer, and an import of a
            ServiceImpl or a published operation would let a write reach back through the layer
            that called it — the cycle the hexagon exists to prevent.
            """.trimIndent(),
        )
        note("Covers the whole of `server.services`, sub-packages included: everything under it is the caller.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isInServerData() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    file.imports
                        .filter { it.name.isServerServicesImport() }
                        .map { Violation(file.path, "server.data imports service code `${it.name}`") }
                }
        }
    }

    @Describe("The `server.data` layer must not import `client` code")
    val noClientImports by rule {
        rationale("The two sides meet at the RPC contract and nowhere else; persistence is the furthest point from that door.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isInServerData() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("feature.") && it.name.contains(".client.") }
                        .map { Violation(file.path, "server.data imports client code `${it.name}`") }
                }
        }
    }

    @Describe("A `server.data` class must provide domain interfaces by exposing them as properties, not by inheriting them")
    val providesDomainImplementations by rule {
        note("Mirrors `ClientData.providesDomainImplementations`; a Repository that inherits an interface fails the enforcing rule directly.")
        enforcedBy("ServerData.Repository.doesNotImplementDomainInterfaces", "ServerData.Repository.exposesDomainInterfacesAsProperties")
    }

    @Describe("A generated Postgres source may only be imported by `server.data`")
    val tableAccessOwnedByStorage by rule {
        rationale(
            """
            The generated `Table` objects and `Row` classes live in the shared
            `platform.server.postgres.tables` package, so any file at all can import one and read or
            write any table. That is the hole beneath every other storage rule: the Storage class
            stops being the single write path for its rows, and the invariants and side effects it
            owns get skipped by whoever went around it. Confining the import to this layer is what
            makes the Storage class the door.
            """.trimIndent(),
        )
        note("`feature.[name].server.data` is the only home a table has: a file that names one from anywhere else is reaching around the Storage class that owns it, whatever package that file is in.")
        note("Covers the whole generated package — the `Table` object is the write path, and an `XxxRow` outside this layer is the persistence shape leaking into a caller that should be reading domain types.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { it.isInServerData() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    file.imports
                        .filter { it.name.isGeneratedTableImport() }
                        .map {
                            Violation(
                                file.path,
                                "imports `${it.name}` outside `server.data` — go through the owning Storage " +
                                    "class so its write path and side effects can't be skipped",
                            )
                        }
                }
        }
    }

    @Describe("A generated table object must be named by exactly one feature")
    val tableOwnedBySingleFeature by rule {
        rationale(
            """
            A table two features write has no single owner, so no one place can hold its invariants
            and every change has to be reasoned about twice. This is the persistence-level
            counterpart to `ModuleRules.crossFeatureCodeViaApi`: reaching another feature's rows is
            the same coupling whether it goes through their Storage class or straight to their table.
            """.trimIndent(),
        )
        note("The feature is the first segment after `feature.`, the same unit every other cross-feature rule uses.")
        note("Tested over every feature file that names a table; `ServerData.tableAccessOwnedByStorage` is what makes the feature that names a table and the feature whose storage owns it the same feature.")
        scope { scope, exempt ->
            val featuresByTable = mutableMapOf<String, MutableMap<String, String>>()
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .forEach { file ->
                    val feature = file.featureName()
                    if (feature.isBlank()) return@forEach
                    file.imports
                        .filter { it.name.matches(generatedTableObjectRegex) }
                        .forEach { import ->
                            featuresByTable.getOrPut(import.name.substringAfterLast('.')) { mutableMapOf() }
                                .putIfAbsent(feature, file.path)
                        }
                }
            featuresByTable
                .filterValues { it.size > 1 }
                .flatMap { (table, sites) ->
                    val features = sites.keys.sorted()
                    features.map { feature ->
                        Violation(
                            sites.getValue(feature),
                            "`$table` is named by ${features.size} features ($features) — it needs one " +
                                "owning feature, reached by the others through that feature's contract",
                        )
                    }
                }
        }
    }

    @Describe("A generated table object must be named by at most one StorageClass file")
    val tableOwnedBySingleStorage by rule {
        rationale(
            """
            A StorageClass is the single write path for the tables it owns, which is what lets its
            invariants and side effects hold: a partial-update shape, a lock order, a counter that
            may not go negative. A second class writing the same table is a second write path, and
            nothing carries those rules across to it — the split is invisible at both call sites and
            shows up as missed rules or missed data. Ownership therefore runs from the table: a class
            may own several tables, and a table has one owner.
            """.trimIndent(),
        )
        note("The sibling of `ServerData.tableOwnedBySingleFeature`, one level down: that rule gives a table one owning feature, this one gives it one owning class.")
        scope { scope, exempt ->
            val filesByTable = mutableMapOf<String, MutableSet<String>>()
            scope.files
                .filter { it.isFeatureModule() && it.isInServerDataStorage() }
                .filterNot { exempt(it) }
                .forEach { file ->
                    file.imports
                        .filter { it.name.matches(generatedTableObjectRegex) }
                        .forEach { import ->
                            filesByTable.getOrPut(import.name.substringAfterLast('.')) { mutableSetOf() }
                                .add(file.path)
                        }
                }
            filesByTable
                .filterValues { it.size > 1 }
                .flatMap { (table, paths) ->
                    val names = paths.map { it.substringAfterLast('/') }.sorted()
                    paths.sorted().map { path ->
                        Violation(
                            path,
                            "`$table` is named by ${paths.size} storage classes ($names) — it needs one " +
                                "owning class, and the others read it through that class",
                        )
                    }
                }
        }
    }

    // ---- Postgres codegen (generated into `platform.server.postgres.tables`) -------------------
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

    @Describe("A `server.data` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root")
    val subsystemVisibility by rule {
        note("`server.data.storage` is the exception and is visible from anywhere in this layer: it is the Row-speaking half of the layer rather than a subsystem, and keeping it one flat surface is what lets a table have a single owning StorageClass.")
        enforcedBy("ProjectRules.subsystemVisibility")
    }

    @Describe("A `server.data` subsystem package imports `server.domain` only through its mirror subsystem, that subsystem's direct children, and their ancestors")
    val subsystemMirrorsDomain by rule {
        note("A [Repository](#repository) at the layer root provides root-declared contracts, unconstrained by the mirror. A mirrored `server.data.[sub]` package provides that subsystem's contracts, through its own Repository-shaped class and/or [IntegrationClients](#integration-client) — a subsystem's edge is a different edge from the layer's.")
        enforcedBy("ProjectRules.subsystemMirrorsDomain")
    }
}

/** A generated Exposed `Table` object — the write path for one SQL table. */
private val generatedTableObjectRegex = Regex("""^platform\.server\.postgres\.tables\.\w*Table$""")

/** True for a file in `feature.[name].server.data` — the file-level form of the group's gate. */
internal fun KoFileDeclaration.isInServerData(): Boolean {
    val pkg = packagee?.name ?: return false
    return pkg.contains(".server.data")
}

/** True for a file in exactly `feature.[name].server.data.storage` — the layer's flat, Row-only subpackage. */
internal fun KoFileDeclaration.isInServerDataStorage(): Boolean {
    val pkg = packagee?.name ?: return false
    return pkg.endsWith(".server.data.storage")
}

/** An import of the services layer — `feature.x.server.services.*`, sub-packages included. */
internal fun String.isServerServicesImport(): Boolean {
    if (!startsWith("feature.")) return false
    return contains(".server.services.")
}
