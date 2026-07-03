> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/services/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Services Layer](../src/main/kotlin/architecture/rules/services/ServicesLayer.kt)

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

##### Constructs

* [Service Interface](#service-interface)
* [Service Impl](#service-impl)
* [Internal Coordinator](#internal-coordinator)
* [Internal Data Carrier](#internal-data-carrier)
* [Internal Interface](#internal-interface)
* [Internal Exception](#internal-exception)
* [Internal Object Helper](#internal-object-helper)
* [Storage Class](#storage-class)
* [Storage Record](#storage-record)
* [Mapping Function](#mapping-function)
* [Codec Object](#codec-object)

##### Rules

* The `services` layer may depend on `domain` and on other features' `:api` `services` contracts; it must not depend on `data`
    * **Why:** The server has no `data` layer, and the client's `data` depends on `services`, not the other way around. Reaching into client-only `data.storage` (Keychain, SharedPreferences) from a services file would fail at runtime or break the client/server split.
* The `services` layer may depend on another feature's `services` only via that feature's `:api` module
    * **Enforced by:** `ModuleRules.clientApiOnly`, `ModuleRules.serverApiOnly`, `ModuleRules.crossFeatureCodeViaApi`
* A class in `services.internal.<subsystem>.**` must not import from a different subsystem under `services.internal` (ancestor data-shape imports are allowed)
    * **Why:** Each direct child of `services.internal` is isolated: a subsystem may use its own children freely, its ancestors only for shared data shapes, and its siblings never. Cross-subsystem composition belongs to the orchestrator at bare `services.internal`, with shared payloads defined at a common ancestor.
* A `services.storage` file must not import from `services.internal`: the dependency direction inside `services` is `internal → storage`
* A declaration placed in `services.tools` may only depend on the Service contract via `:api`-defined types, never on `services.storage` or `services.internal`
    * **Why:** Tools are AI-callable wrappers around the Service contract: they should consume the `:api` Service interface only, not reach into Postgres tables or internal orchestrators directly. The isolation rule is enforced now even though the package is empty.
* A `Table`/`Row` source is generated by the `dev.isaacudy.udytils.postgres` plugin from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables`
    * **Enforced by:** code generation.
* A persisted entity has a generated `object XxxTable : Table("xxx")` (plural); custom columns use the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …)
    * **Enforced by:** code generation.
* A generated `Table` object declares every column on the SQL table, with no omissions; the UUID primary key is `uuid("id").autoGenerate()` but the write path always supplies the id explicitly
    * **Enforced by:** code generation.
* The in-memory persistence shape is a top-level `data class XxxRow` (singular) whose fields use only primitive types — no domain wrappers, enums, or sealed hierarchies
    * **Enforced by:** code generation.
* A generated file exposes a fake-constructor `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes
    * **Enforced by:** code generation.

##### Examples

A Storage class reading via the generated fake-constructor and writing via `setFromRow` (see [generated `Table`/`Row` sources](#generated-tablerow-sources)):

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

---

## [Service Interface](../src/main/kotlin/architecture/rules/services/ServiceInterface.kt)

The client-server contract (in `:api`) and its implementation (in `:server`). Services use
**urpc** (`dev.isaacudy.udytils:urpc-*`): KSP generates the client, the `UrpcService`
server binding, and the wire descriptors from the annotated interface.

* **Note:** Service-level exception conventions (dedicated `@Serializable` exception types,
  `PresentableException`, and the deliberate `retryable` flag) are covered by
  `ServicesLayer.ServiceInterface.errorsViaExceptions` below.

##### Requirements

* A Service Interface resides in `feature..services..`
* A Service Interface is an `interface` annotated `@Urpc`
* A Service Interface is named `[Name]Service`
* A Service Interface resides in the top-level `feature.[name].services` package

##### Rules

* A Service must always be implemented as urpc service functions in the appropriate server module, never as a client-only local service
    * **Verification:** not automatically verifiable; enforced by review.
* A Service function must be a plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, taking 0 or 1 parameter
    * **Note:** The test enforces the parameter count; the suspend/Flow shape is validated by the urpc KSP processor at compile time.
* A Service function's `Request`/`Response` types must be nested `@Serializable` types grouped under a per-function `object` namespace
    * **Verification:** not automatically verifiable; enforced by review.
* A Service interface must live in `feature.[name].services` of the `:api` module
* A Service function must propagate errors via thrown exceptions; the return type only represents a successful result
    * **Why:** `@Throws` on a `suspend` function must include `CancellationException` (or a superclass such as `Exception`); without it, kotlinc rejects the function on iOS targets.
    * **Note:** Known service exceptions should be their own `@Serializable` type (ideally a `PresentableException`).
    * **Note:** `@Throws` on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException`.

##### Examples

A `@Urpc` service contract in `:api`, with nested `@Serializable` `Request`/`Response` types grouped under per-function `object` namespaces:

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

---

## [Service Impl](../src/main/kotlin/architecture/rules/services/ServiceImpl.kt)

An implementation of a `Service` interface (see [Service Interface](#service-interface)). A
ServiceImpl lives in `feature.[name].services` of `:server`, the same package as the contract,
so it belongs to the `services` axis, not the top-level feature group.

##### Requirements

* A Service Impl resides in `feature..services..`
* A Service Impl is named `[Name]ServiceImpl`, matching its `[Name]Service` contract
* A Service Impl resides in `feature.[name].services` of the `:server` module (dual-life with the contract)

##### Rules

* A Service implementation must be `internal`
* A Service implementation must not inject domain interfaces
    * **Why:** A ServiceImpl is the server-side request handler; it reaches down into `services.storage` and `services.internal`, not sideways into the domain interfaces a client would consume.
* A Service implementation must not depend on the `ui` package
    * **Why:** ServiceImpls run on the server and have no Compose runtime: a UI import here would either fail to compile in `:server` or mean a UI type is being treated as data, both of which are wrong. If you need a shape shared with the UI, put it in the feature's `:api` domain or services package.

##### Guidance

* A Service implementation may inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api`

---

## [Internal Coordinator](../src/main/kotlin/architecture/rules/services/InternalCoordinator.kt)

An orchestrator that composes subsystems, such as a `SessionProcessingManager` (see the
[`services.internal` overview](#servicesinternal)). Cross-subsystem composition belongs here,
at bare `services.internal`, not in imports between sibling subsystems.

##### Requirements

* An Internal Coordinator resides in `feature..services..`
* An Internal Coordinator is a concrete (non-`abstract`, non-`data`) class that is not a `Job` or `Exception`
* An Internal Coordinator resides in `feature.[name].services.internal`

---

## [Internal Data Carrier](../src/main/kotlin/architecture/rules/services/InternalDataCarrier.kt)

A payload that flows from one subsystem through the orchestrator into another. A carrier
lives at the bare `services.internal` ancestor so both producer and consumer can name it
under the data-shape exception (see
[hierarchical visibility](#hierarchical-visibility-within-servicesinternal)).

##### Requirements

* An Internal Data Carrier resides in `feature..services..`
* An Internal Data Carrier is a `data class` payload that flows between subsystems through the orchestrator
* An Internal Data Carrier resides in `feature.[name].services.internal`

---

## [Internal Interface](../src/main/kotlin/architecture/rules/services/InternalInterface.kt)

An abstraction used inside a subsystem, such as a strategy contract whose implementations
live in the same subpackage.

##### Requirements

* An Internal Interface resides in `feature..services..`
* An Internal Interface is an interface
* An Internal Interface resides in `feature.[name].services.internal`

---

## [Internal Exception](../src/main/kotlin/architecture/rules/services/InternalException.kt)

An exception thrown only by internal helpers. Service-level exceptions belong on the
`Service` interface (see [Service Interface](#service-interface)).

##### Requirements

* An Internal Exception resides in `feature..services..`
* An Internal Exception is a class named `[Name]Exception`, thrown only by internal helpers
* An Internal Exception resides in `feature.[name].services.internal`

---

## [Internal Object Helper](../src/main/kotlin/architecture/rules/services/InternalObjectHelper.kt)

An `object` that holds pure helper functions.

##### Requirements

* An Internal Object Helper resides in `feature..services..`
* An Internal Object Helper is an object
* An Internal Object Helper resides in `feature.[name].services.internal`

---

## [Storage Class](../src/main/kotlin/architecture/rules/services/StorageClass.kt)

The hand-written entry point to a feature's persistence. See the
[`services.storage` overview](#servicesstorage--postgres-persistence).

##### Requirements

* A Storage Class resides in `feature..services..`
* A Storage Class is named `[Name]Storage` (or `[Name]Store` where the broader name fits)
* A Storage Class is not abstract and not a `data class`
* A Storage Class resides in `feature.[name].services.storage`

##### Rules

* A Storage class must be `internal`
* A Storage class must take and return `XxxRow` types only, never domain types
    * **Why:** Domain conversion lives in mapping functions (`XxxRow.toDomain()`). A Storage method that returns a domain type embeds mapping logic in the persistence layer; the ServiceImpl should do the Row→Domain conversion instead.
* A Storage operation that touches only a subset of columns must use a hand-written `update { … it[col] = value … }` block; `setFromRow` writes every column and is wrong here
    * **Verification:** not automatically verifiable; enforced by review.

---

## [Storage Record](../src/main/kotlin/architecture/rules/services/StorageRecord.kt)

A hand-written persistence record shape: an `XxxRow`/`XxxRecord`/`XxxInsert` `data class`
that lives in a feature's `services.storage`. The generated `XxxRow` classes live in
`platform.server.postgres.tables` instead; see
[generated `Table`/`Row` sources](#generated-tablerow-sources).

##### Requirements

* A Storage Record resides in `feature..services..`
* A Storage Record is a `data class`
* A Storage Record satisfies one of: {is named `[Name]Row`, is named `[Name]Record`, is named `[Name]Insert`}
* A Storage Record resides in `feature.[name].services.storage`

---

## [Mapping Function](../src/main/kotlin/architecture/rules/services/MappingFunction.kt)

A plain `internal fun` conversion between the storage `Row` shapes and domain types.

* **Note:** The convention is `XxxRow.toDomain()` for `Row → Domain` and `Domain.toRow(...)`
  for the inverse.

##### Requirements

* A Mapping Function resides in `feature..services..`
* A Mapping Function is a function
* A Mapping Function resides in `feature.[name].services.storage`

##### Rules

* A Mapping Function between a generated `XxxRow` and a domain type must be a plain `internal fun` declaration in `services.storage`, conventionally collected in `[Name]Mappers.kt`
    * **Verification:** not automatically verifiable; enforced by review.
* A storage operation that spans multiple tables to assemble a richer record must be defined as a higher-level `suspend fun [Name]Storage.loadXxx(…)` extension in `services.storage`
    * **Verification:** not automatically verifiable; enforced by review.

---

## [Codec Object](../src/main/kotlin/architecture/rules/services/CodecObject.kt)

The read/write codec for a column whose on-disk shape differs from the domain shape:
either an `object` holding discriminator constants (such as `ProcessingStatusCodec`) or
file-private `Json` + `encode`/`decode` helpers in the `[Name]Mappers.kt` file.

##### Requirements

* A Codec Object resides in `feature..services..`
* A Codec Object is an object
* A Codec Object lives in `services.storage` alongside the Row + mapping functions for the table that uses it

##### Guidance

* A Codec should stay small and keyed to the column it serves; it encapsulates the read/write asymmetry `setFromRow` can't express
