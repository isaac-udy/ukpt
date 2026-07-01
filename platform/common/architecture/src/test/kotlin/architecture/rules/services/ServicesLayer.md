# The `services` layer

The `services` axis defines the contract that crosses the wire between client and server. The contract lives in `:api` (so both sides see it); the server-side implementation lives in `:server` under the same package name (dual-life). The axis covers both the `:api` Service contract and the entire `:server` implementation surface — ServiceImpls, internal helpers/orchestrators, and Postgres storage.

`services` is **not** a UI-equivalent outer layer — it sits *parallel* to the `data` axis and is consumed by it. On the client, [Repositories](data.md#repositories) (in `data`) inject Service contracts to call the server. On the server, `services` is where the request-handling implementation lives, and reaches down into `services.storage` for persistence and `services.internal.*` for sub-tasks.

The cross-the-wire mechanism is **urpc** (`dev.isaacudy.udytils:urpc-*`): a service is an `@Urpc` interface, and KSP generates the client, the server binding, and the wire descriptors — see [Services (the cross-the-wire contract)](#services-the-cross-the-wire-contract).

## Cross-axis dependencies

Within a feature, the cross-axis dependency rules are:

* `domain` may not depend on any other axis. It is the deepest layer.
* `services` may depend on `domain`.
* `data` (client only) may depend on `domain` and on `services` contracts (so Repositories can call the server).
* `ui` (client only) may depend on `domain` only. It must not depend on `data` or `services` directly — calling the server goes through Repositories, which expose [domain interfaces](domain.md#domain-interfaces) for the UI to consume.
* No axis may depend on `ui`.
* Inside `services`, the dependency direction is `internal → storage` — see [`services.storage`](#servicesstorage--postgres-persistence).

Reading these as a directed graph:

* On the client: `ui → domain ← services ← data` (and `data → domain`).
* On the server: `domain ← services` (with `services` reaching internally into `services.storage` and `services.internal`).

`domain` is the centre of gravity on both sides. `services` is a sibling of `data` (not an outer shell above it) — the wire-crossing contract that `data` consumes on the client and `services` itself implements on the server.

Two layer-level rules pin the axis's place in that graph — `ServicesLayer.mustNotDependOnData` and `ServicesLayer.crossFeatureViaApi`, in the [layer rules](#layer-rules) below.

## `services.internal`

Server-side coordinator and helper classes — the things that do the work the ServiceImpl orchestrates. The bare `services.internal` package holds the top-level orchestrators (e.g. `SessionProcessingManager`) that compose multiple subsystems, plus the shared-payload data types they thread between them; each `services.internal.<subsystem>` package is a sealed island under [hierarchical visibility](#hierarchical-visibility-within-servicesinternal).

The package is modelled by five constructs — [Coordinators](#coordinators-servicesinternal), [data carriers](#data-carriers-servicesinternal), [internal interfaces](#internal-interfaces-servicesinternal), [internal exceptions](#internal-exceptions-servicesinternal), and [object helpers](#object-helpers-servicesinternal) — each requiring its shape plus residence in `feature.[name].services.internal`.

## Hierarchical visibility within `services.internal`

`ServicesLayer.internalHierarchicalVisibility` (in the [layer rules](#layer-rules)) seals each subsystem. Inside `feature.[name].services.internal.**`, an import is allowed only if it points to:

* the **same package**, or
* a **descendant** package, or
* an **ancestor** package, **and only when the imported declaration is a pure data shape**.

Lateral / cousin imports are forbidden outright. Ancestor imports of behaviour-bearing types (regular classes, regular interfaces, top-level functions, objects with member functions) are forbidden too — those would let a subsystem reach back up to *invoke* its parent or use behaviour from a higher level, which re-introduces the cross-subsystem coupling the rule is designed to prevent.

The carve-out for data shapes lets the orchestrator-mediated composition pattern work: a payload type that flows from one subsystem through the orchestrator into another can live at a common ancestor (typically bare `services.internal`), and both subsystems may name it without invoking any behaviour.

A "data shape" is any of:

* `data class`, `enum class`, `value class`, `data object`,
* `sealed class` / `sealed interface`,
* an `object` that holds only `val` constants (no functions).

A subsystem may subdivide into deeper subpackages — the rule applies recursively, so each new subpackage inherits the same sealing rules.

## `services.storage` — Postgres persistence

> **ukpt status**: the Postgres toolkit lives in the `embedded-udytils` submodule (`:postgres-core/koin/codegen/gradle-plugin/embedded`), so these rules are the documented persistence standard. The `:platform:server:postgres` module that applies the codegen plugin and owns the Flyway migrations is **created when the first server feature needs persistence** — until then the `services.storage` rules pass vacuously (no storage code exists yet).

* **Definition**: A feature's persistence storage classes and mappings, built on **[Exposed](https://github.com/JetBrains/Exposed)** and the **`dev.isaacudy.udytils.postgres`** runtime. That runtime (in the `embedded-udytils` submodule, re-exported by `:platform:server:postgres` via `api(libs.udytils.postgres.core)`) provides `PostgresConfig`, `PostgresMigrator`, `PgNotificationBus`, and the custom Exposed column types (`JsonbColumnType`, `JsonColumnType`, `TextArrayColumnType`, `TimestampColumnType`) — do **not** hand-roll these in feature code; extend the library instead.
* **Contents (hand-written, in the feature)**: [Storage classes](#storage-classes-servicesstorage) (`[Name]Storage`), [storage records](#storage-records-servicesstorage), [mapping functions](#mapping-functions-servicesstorage) (conventionally collected in `[Name]Mappers.kt`), and [codec objects](#codec-objects-servicesstorage).
* **Contents (generated, NOT in the feature)**: the Exposed `Table` objects and `XxxRow` data classes are generated into the **shared `platform.server.postgres.tables` package** (`:platform:server:postgres`) and imported by each feature's storage code — see [generated `Table`/`Row` sources](#generated-tablerow-sources) and [the codegen pipeline](#postgres-codegen-pipeline--runtime).

Storage sits at the bottom of the `services` axis — the dependency direction is `internal → storage`, never the reverse (`ServicesLayer.storageMustNotDependOnInternal`, in the [layer rules](#layer-rules)).

### Generated `Table`/`Row` sources

> All `Table`/`Row` codegen rules (`ServicesLayer.generatedTableRowSources` through `ServicesLayer.rowFakeConstructorAndSetFromRow` in the [layer rules](#layer-rules)) are `⚙️ codegen` — guaranteed by the `dev.isaacudy.udytils.postgres` plugin, not by Konsist (the generated sources live under `build/generated/` and are never scanned). They live in the shared `platform.server.postgres.tables` package, not in any feature's `services.storage`, so they are declared as group-level codegen rules rather than feature constructs.

* **Note**: The plugin registers two tasks — `generatePostgresTables` (the Exposed sources) and `exportPostgresSchema` (the committed `schema.sql` snapshot). Generated files live under `build/generated/source/postgres-tables/`, carry a `Generated by the dev.isaacudy.udytils.postgres Gradle plugin` header, and are not committed.
* **Example** (a Storage class reading via the generated fake-constructor and writing via `setFromRow`):
```kotlin
// Read
val row: UserProfileRow? = UserProfilesTable
    .selectAll()
    .where { UserProfilesTable.userId eq userId }
    .singleOrNull()
    ?.let(::UserProfileRow)

// Write
UserProfilesTable.upsert(UserProfilesTable.userId) {
    it.setFromRow(row)
}
```

### Postgres codegen pipeline & runtime

The persistence stack is built on the **`dev.isaacudy.udytils.postgres`** library (developed in the `embedded-udytils` submodule) plus **Exposed**, **Flyway**, and a **Zonky** embedded Postgres:

* **Schema** lives only in `:platform:server:postgres/src/main/resources/db/migration/` as Flyway scripts — versioned `V<n>__snake_name.sql` (run once, in order) and repeatable `R__name.sql` (re-run whenever their checksum changes, e.g. `R__notify_triggers.sql`). A schema change is a **new** `V<n>` file; existing `V<n>` files are never edited in place.
* **`exportPostgresSchema`** Flyway-migrates a throwaway Zonky Postgres and writes a normalised `schema.sql` snapshot; **`generatePostgresTables`** then emits the Exposed `Table`/`Row` sources from it into `platform.server.postgres.tables`. Both tasks are registered by the `dev.isaacudy.udytils.postgres` Gradle plugin and run before `compileKotlin`.
* **Runtime ownership**: the DB primitives (`PostgresConfig`, `PostgresMigrator`, `PgNotificationBus`, the column types) live in the udytils library; `:platform:server:postgres` owns only the SQL migrations + codegen wiring and re-exports the runtime; the **application** (`:app:server`) owns its connection config (`ukptPostgresConfigFromEnv()`), wires `postgresDependencies(config)` (from `dev.isaacudy.udytils.postgres.koin`), and runs `PostgresMigrator.migrate()` before it starts serving.

### Reactive storage flows (`PgNotificationBus`)

A `[Name]Storage` class may expose `Flow` reads that re-query when a Postgres `NOTIFY` fires, by injecting `dev.isaacudy.udytils.postgres.PgNotificationBus`. The channel name is a `companion object const val CHANNEL` and **must** match a `pg_notify(...)` trigger in the migrations (e.g. `R__notify_triggers.sql`). The shape is: emit an initial query, then `bus.listen(CHANNEL).filter { it == key }.collect { emit(query()) }`. This is convention, not a statically-enforced rule.

## `services.tools` (reserved)

* **Definition**: Reserved for AI tool-use subclasses (e.g. `AssistantTool` wrappers around a service). ukpt has no AI subsystem, so `services.tools` is intentionally **empty** — it defines no construct, so any declaration placed here fails the layer-exhaustiveness check until a construct is defined for it.

Its isolation is enforced now, even though the package is empty — `ServicesLayer.toolsApiContractOnly` in the [layer rules](#layer-rules).

* **Note**: If an AI subsystem is added later, reintroduce an `assistantTool` construct (extends `AssistantTool`, named `[Action][Entity]Tool`) on the `ServicesLayer` group to populate this layer.
