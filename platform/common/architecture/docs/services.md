> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Narrative sources: the `ServicesLayer*.md` fragments in `src/test/kotlin/architecture/rules/services/`; structure and rule content come from the rule catalog.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

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

Two layer-level rules pin the axis's place in that graph — `ServicesLayer.mustNotDependOnData` and `ServicesLayer.crossFeatureViaApi`, in the [rules](#rules) below.

## `services.internal`

Server-side coordinator and helper classes — the things that do the work the ServiceImpl orchestrates. The bare `services.internal` package holds the top-level orchestrators (e.g. `SessionProcessingManager`) that compose multiple subsystems, plus the shared-payload data types they thread between them; each `services.internal.<subsystem>` package is a sealed island under [hierarchical visibility](#hierarchical-visibility-within-servicesinternal).

The package is modelled by five constructs — [Coordinators](#coordinators-servicesinternal), [data carriers](#data-carriers-servicesinternal), [internal interfaces](#internal-interfaces-servicesinternal), [internal exceptions](#internal-exceptions-servicesinternal), and [object helpers](#object-helpers-servicesinternal) — each requiring its shape plus residence in `feature.[name].services.internal`.

## Hierarchical visibility within `services.internal`

`ServicesLayer.internalHierarchicalVisibility` (in the [rules](#rules)) seals each subsystem. Inside `feature.[name].services.internal.**`, an import is allowed only if it points to:

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

Storage sits at the bottom of the `services` axis — the dependency direction is `internal → storage`, never the reverse (`ServicesLayer.storageMustNotDependOnInternal`, in the [rules](#rules)).

### Generated `Table`/`Row` sources

> All `Table`/`Row` codegen rules (`ServicesLayer.generatedTableRowSources` through `ServicesLayer.rowFakeConstructorAndSetFromRow` in the [rules](#rules)) are codegen rules — guaranteed by the `dev.isaacudy.udytils.postgres` plugin, not by Konsist (the generated sources live under `build/generated/` and are never scanned). They live in the shared `platform.server.postgres.tables` package, not in any feature's `services.storage`, so they are declared as group-level codegen rules rather than feature constructs.

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

Its isolation is enforced now, even though the package is empty — `ServicesLayer.toolsApiContractOnly` in the [rules](#rules).

* **Note**: If an AI subsystem is added later, reintroduce an `assistantTool` construct (extends `AssistantTool`, named `[Action][Entity]Tool`) on the `ServicesLayer` group to populate this layer.

## Rules

* `services` may depend on `domain` and on other features' `:api` `services` contracts; it must not depend on `data`
    * **ID**: `ServicesLayer.mustNotDependOnData`
    * **Why**: The server has no `data` layer, and the client's `data` depends on `services`, not the other way around. Reaching into client-only `data.storage` (Keychain, SharedPrefs) from a services file would fail at runtime or break the client/server split.
* May depend on another feature's `services` only via that feature's `:api` module
    * **ID**: `ServicesLayer.crossFeatureViaApi`
    * **Enforced by**: `ModuleRules.clientApiOnly`, `ModuleRules.serverApiOnly`
* A class in `services.internal.<subsystem>.**` may not import from a different subsystem under `services.internal` (ancestor data-shape imports are allowed)
    * **ID**: `ServicesLayer.internalHierarchicalVisibility`
    * **Why**: Each direct child of `services.internal` is a sealed island. You can see your children freely, your parents only for shared data shapes, and never your siblings — cross-subsystem composition belongs to the orchestrator at bare `services.internal`, with shared payloads threaded through types that live at a common ancestor.
* Files in `services.storage` must not import from `services.internal` — the dependency direction inside `services` is `internal → storage`
    * **ID**: `ServicesLayer.storageMustNotDependOnInternal`
* Anything placed in `services.tools` may depend on the Service contract via `:api`-defined types only — never on `services.storage` or `services.internal`
    * **ID**: `ServicesLayer.toolsApiContractOnly`
    * **Why**: Tools are AI-callable wrappers around the Service contract — they should consume the `:api` Service interface only, not reach into Postgres tables or internal orchestrators directly. The isolation rule is enforced now even though the package is empty.
* `Table`/`Row` sources are generated by the `dev.isaacudy.udytils.postgres` plugin from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables`
    * **ID**: `ServicesLayer.generatedTableRowSources`
    * **Enforced by**: the `dev.isaacudy.udytils.postgres` code generator
* Each persisted entity has a generated `object XxxTable : Table("xxx")` (plural); custom columns use the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …)
    * **ID**: `ServicesLayer.generatedTableObjects`
    * **Enforced by**: the `dev.isaacudy.udytils.postgres` code generator
* Every column on the SQL table is declared on the `Table` object, with no omissions; the UUID primary key is `uuid("id").autoGenerate()` but the write path always supplies the id explicitly
    * **ID**: `ServicesLayer.everyColumnOnTable`
    * **Enforced by**: the `dev.isaacudy.udytils.postgres` code generator
* The in-memory persistence shape is a top-level `data class XxxRow` (singular) whose fields use only primitive types — no domain wrappers, enums, or sealed hierarchies
    * **ID**: `ServicesLayer.rowDataClassPrimitives`
    * **Enforced by**: the `dev.isaacudy.udytils.postgres` code generator
* Each generated file exposes a fake-constructor `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes
    * **ID**: `ServicesLayer.rowFakeConstructorAndSetFromRow`
    * **Enforced by**: the `dev.isaacudy.udytils.postgres` code generator

## Services (the cross-the-wire contract)

The client-server contract (in `:api`) and its implementation (in `:server`). Services use **urpc** (`dev.isaacudy.udytils:urpc-*`): KSP generates the client, the `UrpcService` server binding, and the wire descriptors from the annotated interface.
* **Note**: Service-level exception conventions — dedicated `@Serializable` exception types, `PresentableException`, and the deliberate `retryable` flag — are covered in [exception handling](exceptions.md).
* **Example**:
```kotlin
// feature.user.services.UserService.kt (:api)
@Urpc
interface UserService {
    suspend fun createUser(request: CreateUser.Request): CreateUser.Response
    suspend fun getUser(request: GetUser.Request): GetUser.Response
    fun observeUsers(): Flow<ObserveUsers.Response>

    object CreateUser {
        @Serializable data class Request(val name: String, val email: String)
        @Serializable data class Response(val user: User)
    }
    // ...
}
```

**Definition** — a declaration is a `ServicesLayer.ServiceInterface` when it satisfies all of:

* resides in `feature..services..`
* A service is an `interface` annotated `@Urpc`
* name ends with `Service`
* Resides in the top-level `feature.[name].services` package

**Rules**:

* Service functions propagate errors via thrown exceptions; the return type only ever represents a successful result
    * **ID**: `ServicesLayer.ServiceInterface.errorsViaExceptions`
    * **Why**: @Throws on suspend functions must include CancellationException (or a superclass like Exception) — required for Kotlin/Native: kotlinc rejects the function on iOS targets otherwise.
    * **Note**: Known service exceptions should be their own `@Serializable` type (ideally a `PresentableException`).
    * **Note**: `@Throws` on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException`.

**Guidance**:

* Always implement services as urpc service functions in the appropriate server module — do not build client-only local services
    * **ID**: `ServicesLayer.ServiceInterface.noClientOnlyServices`
* Functions are plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, each taking 0 or 1 parameter
    * **ID**: `ServicesLayer.ServiceInterface.plainFunctionShapes`
* Each function's `Request`/`Response` types are nested `@Serializable` types grouped under a per-function `object` namespace
    * **ID**: `ServicesLayer.ServiceInterface.nestedRequestResponseTypes`
* Service interfaces live in `feature.[name].services` of the `:api` module
    * **ID**: `ServicesLayer.ServiceInterface.contractLivesInApi`

## Service implementations (`:server`)

Implementations of `Service` interfaces (see [Services](#services-the-cross-the-wire-contract)). A ServiceImpl lives in `feature.[name].services` of `:server` — dual-life with the contract — so it belongs to the `services` axis, not the top-level feature group.

**Definition** — a declaration is a `ServicesLayer.ServiceImpl` when it satisfies all of:

* resides in `feature..services..`
* For a service named `[Name]Service` the implementation is a class named `[Name]ServiceImpl`
* Resides in `feature.[name].services` of the `:server` module (dual-life with the contract)

**Rules**:

* Service implementations must be `internal`
    * **ID**: `ServicesLayer.ServiceImpl.internalVisibility`
* Service implementations must not depend on the `ui` package
    * **ID**: `ServicesLayer.ServiceImpl.noUiDependency`
    * **Why**: ServiceImpls run on the server and have no Compose runtime — a UI import here would either fail to compile in `:server` or mean a UI type has been pulled out of `ui` and is being treated as data, both of which are wrong (§4.4.2, §3.4.4). If you need a shared shape with the UI, put it in the feature's `:api` domain or services package.

**Guidance**:

* Service implementations are forbidden from injecting domain interfaces
    * **ID**: `ServicesLayer.ServiceImpl.noInjectingDomainInterfaces`
    * **Why**: A ServiceImpl is the server-side request handler; it reaches *down* into services.storage and services.internal, not sideways into the domain interfaces a client would consume.
    * **Note**: Surfaced as guidance rather than a construct requirement: forbidding domain-interface injection is a prohibition, not a classification shape, and re-expressing it would require resolving the domain-interface classifier from another layer.
* May inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api`
    * **ID**: `ServicesLayer.ServiceImpl.mayInjectStorageAndInternal`

## Coordinators (`services.internal`)

The orchestrators that compose subsystems (e.g. `SessionProcessingManager`) — see the [`services.internal` overview](#servicesinternal). Cross-subsystem composition belongs here, at bare `services.internal`, not to imports between sibling subsystems.

**Definition** — a declaration is a `ServicesLayer.InternalCoordinator` when it satisfies all of:

* resides in `feature..services..`
* A coordinator is a concrete (non-`abstract`, non-`data`) class that is not a `Job` or `Exception`
* Resides in `feature.[name].services.internal`

## Data carriers (`services.internal`)

Payloads that flow from one subsystem through the orchestrator into another. A carrier lives at the bare `services.internal` ancestor so both producer and consumer can name it under the data-shape carve-out (see [hierarchical visibility](#hierarchical-visibility-within-servicesinternal)).

**Definition** — a declaration is a `ServicesLayer.InternalDataCarrier` when it satisfies all of:

* resides in `feature..services..`
* A data carrier is a `data class` payload that flows between subsystems through the orchestrator
* Resides in `feature.[name].services.internal`

## Internal interfaces (`services.internal`)

Abstractions used inside a subsystem (e.g. a strategy contract whose implementations live in the same subpackage).

**Definition** — a declaration is a `ServicesLayer.InternalInterface` when it satisfies all of:

* resides in `feature..services..`
* is an interface
* Resides in `feature.[name].services.internal`

## Internal exceptions (`services.internal`)

Exceptions thrown only by internal helpers; service-level exceptions belong on the `Service` interface (see [Services](#services-the-cross-the-wire-contract)).

**Definition** — a declaration is a `ServicesLayer.InternalException` when it satisfies all of:

* resides in `feature..services..`
* An internal exception is a class named `[Name]Exception`, thrown only by internal helpers
* Resides in `feature.[name].services.internal`

## Object helpers (`services.internal`)

`object`s holding pure helper functions.

**Definition** — a declaration is a `ServicesLayer.InternalObjectHelper` when it satisfies all of:

* resides in `feature..services..`
* is an object
* Resides in `feature.[name].services.internal`

## Storage classes (`services.storage`)

The hand-written entry point to a feature's persistence — see the [`services.storage` overview](#servicesstorage--postgres-persistence).

**Definition** — a declaration is a `ServicesLayer.StorageClass` when it satisfies all of:

* resides in `feature..services..`
* Named `[Name]Storage` (or `[Name]Store` where the broader name fits)
* Not abstract, not a `data class`
* Resides in `feature.[name].services.storage`

**Rules**:

* Storage classes must be `internal`
    * **ID**: `ServicesLayer.StorageClass.internalVisibility`
* Storage classes must take/return `XxxRow` types only — never domain types
    * **ID**: `ServicesLayer.StorageClass.returnsRowTypesOnly`
    * **Why**: Domain conversion lives in mapping functions (`XxxRow.toDomain()`). A Storage method that returns a domain type embeds mapping logic in the persistence layer; the ServiceImpl should do the Row→Domain conversion instead.

**Guidance**:

* When an operation touches only a subset of columns, keep the hand-written `update { … it[col] = value … }` block — `setFromRow` writes every column and is wrong here
    * **ID**: `ServicesLayer.StorageClass.partialUpdatesByHand`

## Storage records (`services.storage`)

The hand-written persistence record shapes — the `XxxRow`/`XxxRecord`/`XxxInsert` `data class`es that live in a feature's `services.storage`. The *generated* `XxxRow` classes live in `platform.server.postgres.tables` instead — see [generated `Table`/`Row` sources](#generated-tablerow-sources).

**Definition** — a declaration is a `ServicesLayer.StorageRecord` when it satisfies all of:

* resides in `feature..services..`
* Is a `data class`
* one of {name ends with `Row`, name ends with `Record`, name ends with `Insert`}
* Resides in `feature.[name].services.storage`

## Mapping functions (`services.storage`)

* **Convention**: `XxxRow.toDomain()` for `Row → Domain`; `Domain.toRow(...)` for the inverse.

**Definition** — a declaration is a `ServicesLayer.MappingFunction` when it satisfies all of:

* resides in `feature..services..`
* is a function
* Resides in `feature.[name].services.storage`

**Guidance**:

* Conversions between a generated `XxxRow` and a domain type live in `services.storage` as plain `internal fun` declarations, conventionally collected in `[Name]Mappers.kt`
    * **ID**: `ServicesLayer.MappingFunction.mappersInStorage`
* Where storage operations span multiple tables to assemble a richer record, define those higher-level helpers as `suspend fun [Name]Storage.loadXxx(…)` extensions in `services.storage`
    * **ID**: `ServicesLayer.MappingFunction.multiTableLoadHelpers`

## Codec objects (`services.storage`)

The read/write codec for a column whose on-disk shape differs from the domain shape — either an `object` holding discriminator constants (e.g. `ChatMessageContentTypeCodec`, `ProcessingStatusCodec`) or file-private `Json` + `encode`/`decode` helpers in the `[Name]Mappers.kt` file.

**Definition** — a declaration is a `ServicesLayer.CodecObject` when it satisfies all of:

* resides in `feature..services..`
* is an object
* Lives in `services.storage` alongside the Row + mapping functions for the table that uses it

**Guidance**:

* Codecs encapsulate the read/write asymmetry `setFromRow` can't express — keep them small and keyed to the column they serve
    * **ID**: `ServicesLayer.CodecObject.keyedToColumn`
