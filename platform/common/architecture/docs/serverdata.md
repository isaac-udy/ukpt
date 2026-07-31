> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/serverdata/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Server Data](../src/main/kotlin/architecture/rules/serverdata/ServerData.kt)

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

##### Constructs

* [Repository](#repository)
* [Storage Class](#storage-class)
* [Storage Record](#storage-record)
* [Codec Object](#codec-object)
* [Mapping Function](#mapping-function)
* [Integration Client](#integration-client)

##### Rules

* The `server.data` layer must never import `server.services`
    * **Why:** Persistence exists to satisfy the domain, not to serve requests. An import of a service contract would put the wire format inside the storage layer, and an import of a ServiceImpl or a published operation would let a write reach back through the layer that called it — the cycle the hexagon exists to prevent.
    * **Note:** Covers the whole of `server.services`, sub-packages included: everything under it is the caller.
* The `server.data` layer must not import `client` code
    * **Why:** The two sides meet at the RPC contract and nowhere else; persistence is the furthest point from that door.
* A `server.data` class must provide domain interfaces by exposing them as properties, not by inheriting them
    * **Note:** Mirrors `ClientData.providesDomainImplementations`; a Repository that inherits an interface fails the enforcing rule directly.
    * **Enforced by:** `ServerData.Repository.doesNotImplementDomainInterfaces`, `ServerData.Repository.exposesDomainInterfacesAsProperties`
* A generated Postgres source may only be imported by `server.data`, and a generated `Table` object only by the `server.data.storage` package
    * **Why:** The generated `Table` objects and `Row` classes live in the shared `platform.server.postgres.tables` package, so any file at all can import one and read or write any table. That is the hole beneath every other storage rule: the Storage class stops being the single write path for its rows, and the invariants and side effects it owns get skipped by whoever went around it. The two generated shapes confine differently: an `XxxRow` is data, which the layer's mapping functions legitimately speak, but a `Table` object is the query and write path itself — a Repository holding one has bypassed the StorageClass this rule exists to make the door.
    * **Note:** `feature.[name].server.data` is the only home a table has: a file that names one from anywhere else is reaching around the Storage class that owns it, whatever package that file is in.
    * **Note:** Within the layer, `Row` imports are legal anywhere — mapping functions at the data root take Rows as receivers — while `Table` imports are legal only in the flat `storage` package, where the owning StorageClass lives.
* A generated table object must be named by exactly one feature
    * **Why:** A table two features write has no single owner, so no one place can hold its invariants and every change has to be reasoned about twice. This is the persistence-level counterpart to `ModuleRules.crossFeatureCodeViaApi`: reaching another feature's rows is the same coupling whether it goes through their Storage class or straight to their table.
    * **Note:** The feature is the first segment after `feature.`, the same unit every other cross-feature rule uses.
    * **Note:** Tested over every feature file that names a table; `ServerData.tableAccessOwnedByStorage` is what makes the feature that names a table and the feature whose storage owns it the same feature.
* A generated table object must be named by at most one StorageClass file
    * **Why:** A StorageClass is the single write path for the tables it owns, which is what lets its invariants and side effects hold: a partial-update shape, a lock order, a counter that may not go negative. A second class writing the same table is a second write path, and nothing carries those rules across to it — the split is invisible at both call sites and shows up as missed rules or missed data. Ownership therefore runs from the table: a class may own several tables, and a table has one owner.
    * **Note:** The sibling of `ServerData.tableOwnedBySingleFeature`, one level down: that rule gives a table one owning feature, this one gives it one owning class.
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
* A `server.data` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Note:** `server.data.storage` is the exception and is visible from anywhere in this layer: it is the Row-speaking half of the layer rather than a subsystem, and keeping it one flat surface is what lets a table have a single owning StorageClass.
    * **Enforced by:** `ProjectRules.subsystemVisibility`
* A `server.data` subsystem package imports `server.domain` only through its mirror subsystem, that subsystem's direct children, and their ancestors
    * **Note:** A [Repository](#repository) at the layer root provides root-declared contracts, unconstrained by the mirror. A mirrored `server.data.[sub]` package provides that subsystem's contracts, through its own Repository-shaped class and/or [IntegrationClients](#integration-client) — a subsystem's edge is a different edge from the layer's.
    * **Enforced by:** `ProjectRules.subsystemMirrorsDomain`

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

## [Repository](../src/main/kotlin/architecture/rules/serverdata/Repository.kt)

The edge of [`server.domain`](serverdomain.md): a class that provides
[server domain interfaces](serverdomain.md#domain-interface) by exposing them as `public val`
properties, injecting the [StorageClasses](#storage-class) it reads and writes through and
mapping their [Rows](#storage-record) into domain objects.

It is the [client Repository](clientdata.md#repository) on the other side, with the same name and
the same rules. The difference is only what sits behind it: a Service and local storage on the
client, tables on the server.

* **Note:** The property name must match the interface name in `lowerCamelCase`, such as
  `val createUser = CreateUser { ... }`.

##### Requirements

* A Repository resides in `feature..server.data..`
* A Repository is a class
* A Repository is named `[Name]Repository`
* A Repository is declared in a file matching its name

##### Rules

* A Repository must be `internal`
    * **Why:** Callers depend on the domain interfaces it provides, never on the Repository itself; `internal` is what makes that the only reachable surface.
* A Repository must not implement domain interfaces directly
    * **Why:** Inheriting the interface makes one class *be* many contracts, so its surface can only grow; exposing them as properties keeps each contract separately nameable and separately injectable.
    * **Note:** A parent reference is resolved through its file's imports and matched against the side's classified domain interfaces by fully-qualified name — an `:api`-declared parent often resolves to no source declaration, and a simple-name match would collide with unrelated types sharing the name.
* A Repository must expose domain interfaces as `public val` properties
    * **Why:** The property name is the interface name in lowerCamelCase, so the wiring reads as a list of the contracts this Repository answers.
* A Repository must not inject domain interfaces
    * **Why:** A Repository that injects a contract is calling a sibling adapter through the abstract layer, which makes the graph unreadable and easy to cycle. Logic that needs several interfaces is a UseCase.
    * **Note:** A parameter type — bare, aliased, or inside a wrapper such as `Lazy<…>` — is resolved through its file's imports and matched against the side's classified domain interfaces by fully-qualified name.
* A Repository must not inject other Repositories
    * **Why:** Two Repositories over one domain object have no single edge, and the second reaches its data through the first's mapping rather than through the source that owns it.
* A Repository's domain-interface properties must be initialized immediately: no `by lazy`, no custom getter
    * **Why:** Eager initialisation lets Koin's graph validation catch a missing or cyclic dependency at startup rather than at first use, and it makes the wiring obvious from a quick read of the constructor.

##### Guidance

* A Repository may inject the StorageClasses it needs, and compose several of them behind one domain interface
    * **Note:** A domain object may span several tables. Composing them is this class's job — the StorageClass under each is free to own several tables of its own, and every table has exactly one such owner.
* A Repository must not call an IntegrationClient from inside a `TransactionRunner.inTransaction` block
    * **Note:** The block holds a pooled database connection, and any row locks it has taken, for as long as it runs — a network round trip inside it starves the pool for that whole time. Make the integration call first and open the transaction with its result in hand.

##### Examples

A Repository providing two domain interfaces over a table its StorageClass owns, mapping Rows on the way out:

```kotlin
internal class UsersRepository(
    private val userStorage: UserStorage,
    private val userRoleStorage: UserRoleStorage,
) {
    val getUser = GetUser { id ->
        userStorage.getById(id)?.toDomain()
    }

    val flowOfUsersForTeam = FlowOfUsersForTeam { teamId ->
        userStorage.observeForTeam(teamId).map { rows ->
            rows.map { it.toDomain() }
        }
    }
}
```

A domain object that spans two tables is composed here, not in either StorageClass:

```kotlin
val getUserWithRoles = GetUserWithRoles { id ->
    val user = userStorage.getById(id) ?: return@GetUserWithRoles null
    val roles = userRoleStorage.listForUser(id)
    user.toDomain(roles = roles.map { it.toDomain() })
}
```

---

## [Storage Class](../src/main/kotlin/architecture/rules/serverdata/StorageClass.kt)

The single write path for the tables it owns, and the only place their queries are written. A
StorageClass speaks [Rows](#storage-record): it takes and returns persistence shapes, and names
no domain type at all. It lives in `feature.[name].server.data.storage`, the layer's Row-only
subpackage, which mirrors [`client.data.storage`](clientdata.md#client-storage). The
[Repository](#repository) above it, at the `server.data` root, injects it, maps what it returns,
and provides the [domain interfaces](serverdomain.md#domain-interface) callers actually hold. See
the [`server.data` overview](serverdata.md).

**Ownership runs from the table.** A StorageClass may own several tables, and should when they
change together — a reservation that locks and increments two counters in one transaction has one
set of invariants, and two classes would hold half of it each. What a table may not have is two
owners: `ServerData.tableOwnedBySingleStorage` gives every table exactly one class that writes
it, so its rules and side effects live in one place and cannot be skipped by going around it.

##### Requirements

* A Storage Class resides in `feature..server.data..`
* A Storage Class is named `[Name]Storage` (or `[Name]Store` where the broader name fits)
* A Storage Class is not abstract and not a `data class`
* A Storage Class resides in `feature.[name].server.data.storage`

##### Rules

* A Storage class must be `internal`
* A Storage class must take and return `XxxRow` types only, never domain types
    * **Why:** The query and the mapping are two things, and this class is the query. Domain conversion is the [Repository](serverdata.md#repository)'s job, through this layer's [mapping functions](serverdata.md#mapping-function) (`XxxRow.toDomain()`). A Storage method that returns a domain type has folded the mapping into the query, so neither can be reused or read on its own — and it has taken a decision that belongs one level up, where a domain object may be composed from more than one table.
* A Storage operation that touches only a subset of columns must use a hand-written `update { … it[col] = value … }` block; `setFromRow` writes every column and is wrong here
    * **Verification:** not automatically verifiable; enforced by review.
* A Storage function's name must begin with a declared CRUD verb, so reads and writes are distinguishable by name
    * **Why:** The rules around this layer distinguish reads from writes, and a call site can only be read that way if the function's *name* says which it is. `flowForSession` and `touch` do not. A declared verb makes "this call mutates a table" visible at every site that makes it, without opening the Storage class.
    * **Note:** Reads: `get`, `list`, `count`, `observe`. Writes: `insert`, `update`, `upsert`, `delete`, `replace`.
    * **Note:** Plus a closed set of transition verbs for state machines, where a generic write verb loses the meaning — `claimNext` says more than `updateClaimNext`: `claim`, `release`, `reserve`, `reap`, `enqueue`, `heartbeat`, `succeed`, `fail`, `grant`, `revoke`. Adding to that list is a deliberate edit to this rule.
* A StorageClass must not import `server.domain`
    * **Why:** This class speaks Rows. Naming a domain type here means the mapping, or the decision about which tables make up a domain object, has moved down into a query — and the [Repository](serverdata.md#repository) whose job that is has been bypassed. Keeping the layer out of `server.domain` entirely is what makes `returnsRowTypesOnly` hold at every other point of the class, not just its return types.
    * **Note:** Tested per file, so a mapping function or codec sharing the file is covered by the same import test.
* A StorageClass must not inject another StorageClass
    * **Why:** Ownership runs from the table, and one Storage class reaching into another is how a table acquires a second writer: the reaching class writes rows it does not own, through a path the owner's invariants do not cover. A class that needs tables it does not own is composing, and composing belongs to the Repository above them.
* A StorageClass must not inject a Repository
    * **Why:** The Repository is above this class and reads through it; injecting it inverts the layer and puts the mapping back inside the query.

---

## [Storage Record](../src/main/kotlin/architecture/rules/serverdata/StorageRecord.kt)

A hand-written persistence record shape: an `XxxRow`/`XxxRecord`/`XxxInsert` `data class`
that lives in a feature's `server.data.storage`, alongside the
[StorageClass](#storage-class) that returns it. The generated `XxxRow` classes live in
`platform.server.postgres.tables` instead; see
[generated `Table`/`Row` sources](serverdata.md).

##### Requirements

* A Storage Record resides in `feature..server.data..`
* A Storage Record is a `data class`
* A Storage Record satisfies one of: {is named `[Name]Row`, is named `[Name]Record`, is named `[Name]Insert`}
* A Storage Record resides in `feature.[name].server.data.storage`

---

## [Codec Object](../src/main/kotlin/architecture/rules/serverdata/CodecObject.kt)

The read/write codec for a column whose on-disk shape differs from the domain shape:
either an `object` named `[Name]Codec` holding discriminator constants (such as
`ProcessingStatusCodec`) or file-private `Json` + `encode`/`decode` helpers in the
`[Name]Mappers.kt` file.

* **Note:** The name is what classifies it: "an `object` in `server.data`" would claim any
  other object the layer holds.

##### Requirements

* A Codec Object resides in `feature..server.data..`
* A Codec Object is an object
* A Codec Object is named `[Name]Codec`
* A Codec Object resides in `feature.[name].server.data`, outside the `storage` subtree

##### Guidance

* A Codec should stay small and keyed to the column it serves; it encapsulates the read/write asymmetry `setFromRow` can't express

---

## [Mapping Function](../src/main/kotlin/architecture/rules/serverdata/MappingFunction.kt)

A plain `internal fun` conversion between the storage `Row` shapes and domain types.

* **Note:** The convention is `XxxRow.toDomain()` for `Row → Domain` and `Domain.toRow(...)`
  for the inverse.

##### Requirements

* A Mapping Function resides in `feature..server.data..`
* A Mapping Function is a function
* A Mapping Function resides in `feature.[name].server.data`, outside the `storage` subtree

##### Rules

* A Mapping Function between a generated `XxxRow` and a domain type must be a plain `internal fun` declaration in `server.data`, conventionally collected in `[Name]Mappers.kt`
    * **Verification:** not automatically verifiable; enforced by review.
* A storage operation that spans multiple tables to assemble a richer record must be defined as a higher-level `suspend fun [Name]Storage.loadXxx(…)` extension in `server.data`
    * **Verification:** not automatically verifiable; enforced by review.

---

## [Integration Client](../src/main/kotlin/architecture/rules/serverdata/IntegrationClient.kt)

An adapter onto something outside the process — a GenAI provider, a transcription service, an
email sender, object storage. The [Repository](serverdata.md#repository) idea pointed outward
instead of at a table, and it satisfies a [domain interface](serverdomain.md#domain-interface)
the same two ways: by exposing it as a property, or by implementing it directly where the client
*is* the whole of the contract.

An integration exists to satisfy a domain interface stated in the server's own terms, not the
vendor's. `TranscribeAudio` is the contract; that it is currently Gemini is this class's business
and nothing else's. Swapping the provider should change one file.

##### Requirements

* An Integration Client resides in `feature..server.data..`
* An Integration Client is a class
* An Integration Client is named `[Name]Client` or `[Name]Provider`
* An Integration Client is not abstract and not a `data class`

##### Rules

* An IntegrationClient must be `internal`
    * **Why:** The vendor is an implementation detail. Callers depend on the domain interface it provides, never on the client itself.
* An IntegrationClient must not leak vendor types through the domain interface it provides
    * **Verification:** not automatically verifiable; enforced by review.
