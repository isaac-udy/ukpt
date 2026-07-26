> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/services/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Services Layer](../src/main/kotlin/architecture/rules/services/ServicesLayer.kt)

The `services` axis defines the contract between client and server. The contract lives in
`:api`, so both sides see it; the server-side implementation lives in `:server` under the same
package name. The axis covers the `:api` Service contract and the whole `:server` implementation
surface — ServiceImpls, internal helpers and orchestrators, and Postgres storage — all under the
one `feature..services..` package tree, so it is a single RuleGroup whose Constructs' package
requirements keep the sub-axes (`internal`, `storage`, `tools`) disjoint.

`services` is a sibling of the `data` axis, not an outer shell above it. On the client,
[Repositories](data.md#repository) (in `data`) inject Service contracts to call the server; on
the server, `services` is where request handling lives, reaching into `services.storage` for
persistence and `services.internal` for sub-tasks. Communication uses **urpc**
(`dev.isaacudy.udytils:urpc-*`): a service is an `@Urpc` interface, and KSP generates the client,
the server binding, and the wire descriptors. See [Service Interface](#service-interface).

Within a feature the cross-axis rules are: `domain` depends on nothing; `services` may depend on
`domain`; client `data` may depend on `domain` and on `services` contracts (so Repositories can
call the server); client `ui` may depend on `domain` only; and inside `services` the direction is
`internal → storage`. Two layer-level rules pin the axis into that graph —
`ServicesLayer.mustNotDependOnData` and `ServicesLayer.crossFeatureViaApi` (in the [rules](#rules)).
No axis may depend on `ui`; the dependency graph is summarised under the [domain layer](domain.md).

> **Most of the `:server` surface is deferred.** The base template ships no server feature — the
> only worked example, `feature.ukpt`, is client-side, and `:feature:core:server` is an empty
> stub. The `services.internal`, `services.storage`, and `services.tools` conventions below are
> the documented standard for when a feature first needs them; until then their rules pass
> vacuously, with no code to apply to.

## `services.internal`

Server-side coordinators and helpers — the work a ServiceImpl orchestrates. The bare
`services.internal` package holds the top-level orchestrators that compose multiple subsystems
(such as a `SessionProcessingManager`) plus the shared-payload data types they pass between
them; each `services.internal.<subsystem>` is isolated under
[hierarchical visibility](#hierarchical-visibility-within-servicesinternal). The package is
modelled by five Constructs — [coordinators](#internal-coordinator),
[data carriers](#internal-data-carrier), [internal interfaces](#internal-interface),
[internal exceptions](#internal-exception), and [object helpers](#internal-object-helper) — each
residing in `feature.[name].services.internal`.

### Hierarchical visibility within `services.internal`

`ServicesLayer.internalHierarchicalVisibility` (in the [rules](#rules)) isolates each subsystem.
Inside `feature.[name].services.internal.**`, an import is allowed only from the same package, a
descendant, or an ancestor — and an ancestor import only when the target is a pure data shape.
Lateral and cousin imports are forbidden outright, as are ancestor imports of behaviour-bearing
types, so a subsystem can never invoke its parent or a sibling; cross-subsystem composition
belongs to the orchestrator at bare `services.internal`, with shared payloads defined at a common
ancestor. A "data shape" is a `data`/`enum`/`value` class, a `data object`, a `sealed` type, or a
constants-only `object`. The rule applies recursively to deeper subpackages.

## `services.storage` — Postgres persistence

A feature's persistence — [storage classes](#storage-class) (`[Name]Storage`),
[storage records](#storage-record), [mapping functions](#mapping-function) (conventionally in
`[Name]Mappers.kt`), and [codec objects](#codec-object) — built on **Exposed** and the
**`dev.isaacudy.udytils.postgres`** runtime (in the `embedded-udytils` submodule, re-exported by
`:platform:server:postgres`). That runtime provides `PostgresConfig`, `PostgresMigrator`,
`PgNotificationBus`, and the custom Exposed column types; extend the library rather than
re-implementing them. Storage sits at the bottom of the axis — the direction is
`internal → storage`, never the reverse (`ServicesLayer.storageMustNotDependOnInternal`, in the
[rules](#rules)).

### Generated `Table`/`Row` sources

The Exposed `Table` objects and `XxxRow` data classes are **generated** by the
`dev.isaacudy.udytils.postgres` Gradle plugin from the Flyway-migrated schema, into the shared
`platform.server.postgres.tables` package — not in any feature — so the codegen rules
(`ServicesLayer.generatedTableRowSources` through `ServicesLayer.rowFakeConstructorAndSetFromRow`,
in the [rules](#rules)) are guaranteed by the plugin, not by tests. A Storage class reads via the
generated fake-constructor and writes via the `setFromRow` extension; see the layer examples
after the [rules](#rules). A `[Name]Storage` may also expose `Flow` reads that re-query on a
Postgres `NOTIFY` via `PgNotificationBus`. The codegen pipeline, Flyway migration conventions,
and runtime wiring (`:app:server` owns the connection config and runs the migrator) are
documented by the `dev.isaacudy.udytils.postgres` toolkit in the `embedded-udytils` submodule.

## `services.tools` (reserved)

Reserved for AI tool-use subclasses (such as `AssistantTool` wrappers around a service). ukpt
has no AI subsystem, so `services.tools` is intentionally empty — it defines no Construct, and
any declaration placed there fails the exhaustiveness test until one is added. Its isolation is
enforced even while empty: see `ServicesLayer.toolsApiContractOnly` in the [rules](#rules). When
an AI subsystem arrives, add an `assistantTool` Construct (extends `AssistantTool`, named
`[Action][Entity]Tool`) to this group.

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

> **Illustrative.** No feature ships Postgres storage yet — `:platform:server:postgres` (which generates the `Table`/`Row` sources) is created with the first server feature that needs persistence. The read/write below shows the shape against those generated types.

A Storage class reading via the generated fake-constructor and writing via `setFromRow` (see [generated `Table`/`Row` sources](#generated-tablerow-sources)):

```kotlin
// Read
val row: UserProfileRow? = UserProfilesTable
    .selectAll()
    .where { UserProfilesTable.userId eq userId }
    .singleOrNull()
    ?.let(::UserProfileRow)

// Write (rowToWrite is a non-null UserProfileRow)
UserProfilesTable.upsert(UserProfilesTable.userId) {
    it.setFromRow(rowToWrite)
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

> **Illustrative.** `:feature:core` defines no `@Urpc` contract in `:api` (and its `:server` is an empty stub). The contract below shows the shape; add a real one with the `ukpt-urpc-service` skill.

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
