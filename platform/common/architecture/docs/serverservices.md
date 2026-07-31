> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/serverservices/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Server Services](../src/main/kotlin/architecture/rules/serverservices/ServerServices.kt)

`feature.[name].server.services` defines the contract between client and server, and the
server's entry points. The contract lives in `:api`, so both sides see it; the implementation
lives in `:server` under the same package name.

Everything else in the layer is an **entry point** — a class something outside the process
triggers. The template ships one kind: a [ServiceImpl](#service-impl) answering a network
request. The work an entry point triggers is not declared here — it is stated as
[`server.domain` interfaces](serverdomain.md#domain-interface) and done by
[UseCases](serverdomain.md#use-case).

On the client, [Repositories](clientdata.md#repository) (in `client.data`) inject Service
contracts to call the server. On the server, an entry point composes domain interfaces;
persistence sits behind them, in [`server.data`](serverdata.md), which this layer never imports.

Client/server communication uses **urpc** (`dev.isaacudy.udytils:urpc-*`): a service is an
`@Urpc` interface, and KSP generates the client, the server binding, and the wire descriptors.
See [Service Interface](#service-interface).

## Cross-layer dependencies

Within a feature, the layer dependency rules are:

* Each side's `domain` imports feature roots only. It is the middle of its hexagon.
* `server.services` may depend on [`server.domain`](serverdomain.md) — and never on
  [`server.data`](serverdata.md) (`ServerServices.noDataImports`).
* `server.data` may depend on `server.domain` — and never on `server.services`
  (`ServerData.noServiceImports`).
* `client.data` may depend on `client.domain` and on this layer's contracts, so Repositories
  can call the server (`ClientData.clientServerDependencyRestriction`).
* `client.ui` may depend on `client.domain` only; server calls go through
  [Repositories](clientdata.md#repository), which provide
  [domain interfaces](clientdomain.md#domain-interface) for the UI to consume.
* Nothing depends on `client.ui`.

Reading these as a directed graph:

* On the client: `client.ui → client.domain ← client.data`.
* On the server: `server.services → server.domain ← server.data`.

The two sides meet only at the contract this layer declares in `:api`. Cross-feature use of
another feature's services goes through `:api` as well: `ServerServices.crossFeatureViaApi`,
in the [rules](#rules) below.

On the server, that contract is a door, not a composition mechanism: a class in this layer
never injects another feature's Service contract
(`ServerServices.noForeignServiceContractInjection`). One server feature reaches another
through the capability the owner publishes — a
[`server.domain` interface](serverdomain.md#domain-interface) whose file resides in `:api` —
which is the same channel the domain layers use.

## Sub-packages

Any sub-package of the layer is an ordinary subsystem under
`ProjectRules.subsystemVisibility`: it sees its own package, its direct children, and its
ancestors up to the layer root — never a sibling.

## Persistence

The server's persistence layer is [`server.data`](serverdata.md). Entry points reach it only
through [`server.domain` interfaces](serverdomain.md#domain-interface), which a
[Repository](serverdata.md#repository) provides (`ServerServices.noDataImports`); the Postgres
conventions, codegen pipeline, and reactive flows are documented on that layer's page.

##### Constructs

* [Service Interface](#service-interface)
* [Service Impl](#service-impl)

##### Rules

* The `server.services` layer must never import `server.data`
    * **Why:** This is the hexagon. `server.domain` sits between services and persistence and knows neither: services consume domain interfaces, and Repositories provide them. A ServiceImpl that reaches a table directly has skipped the layer where the contract should have been stated, so nothing else can reuse that access, and nothing names what the service actually needed.  `ServerData.noServiceImports` is the other half. Together they make storage a thing that *satisfies* a stated need rather than a thing services reach through.
    * **Note:** Tested over imports of persistence, wherever the imported file sits: reaching a table is the same act whatever the package holding it is called.
* The `server.services` layer must not import client code
    * **Why:** The two sides meet at the RPC contract and nowhere else.
* The `services` layer may depend on another feature's `services` only via that feature's `:api` module
    * **Enforced by:** `ModuleRules.clientApiOnly`, `ModuleRules.serverApiOnly`, `ModuleRules.crossFeatureCodeViaApi`
* A class in `server.services` must not inject another feature's Service contract
    * **Why:** A Service contract is the door between a client and this server, not a way for one server feature to call another. Injecting another feature's contract gives a server-internal call the wire's shape — a request object, a session the caller must already hold, an error type written for a screen — and hands the caller every operation on that service when it needed one capability. That capability is what the owning feature's [`server.domain` interfaces](serverdomain.md#domain-interface) say: the owner publishes the narrow one to `:api`, a Repository provides it, and the caller states it like any other contract.
    * **Note:** Tested on the primary constructor of every class in `feature.[name].server.services` and its sub-packages: a parameter whose type — bare, or inside a wrapper such as `Lazy<…>` — is an `@Urpc` interface belonging to another feature.
    * **Note:** A feature's own contract is unaffected: a class in this layer may wrap or delegate to its own feature's Service.
* A `server.services` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Enforced by:** `ProjectRules.subsystemVisibility`
* A `server.services` subsystem package imports `server.domain` only through its mirror subsystem, that subsystem's direct children, and their ancestors
    * **Note:** A file at the layer root — a ServiceImpl — is unconstrained by the mirror and sees the whole of `server.domain`.
    * **Enforced by:** `ProjectRules.subsystemMirrorsDomain`

---

## [Service Interface](../src/main/kotlin/architecture/rules/serverservices/ServiceInterface.kt)

The client-server contract (in `:api`) and its implementation (in `:server`). Services use
**urpc** (`dev.isaacudy.udytils:urpc-*`): KSP generates the client, the `UrpcService`
server binding, and the wire descriptors from the annotated interface.

* **Note:** Service-level exception conventions (dedicated `@Serializable` exception types,
  `PresentableException`, and the deliberate `retryable` flag) are covered by
  `ServerServices.ServiceInterface.errorsViaExceptions` below.

##### Requirements

* A Service Interface resides in `feature..server.services..`
* A Service Interface is an `interface` annotated `@Urpc`
* A Service Interface is named `[Name]Service`
* A Service Interface resides in `feature.[name].server.services` itself, not in a sub-package

##### Rules

* A Service must always be implemented as urpc service functions in the appropriate server module, never as a client-only local service
    * **Verification:** not automatically verifiable; enforced by review.
* A Service function must be a plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, taking 0 or 1 parameter
    * **Note:** The test enforces the parameter count; the suspend/Flow shape is validated by the urpc KSP processor at compile time.
* A Service function's `Request`/`Response` types must be nested `@Serializable` types grouped under a per-function `object` namespace
    * **Verification:** not automatically verifiable; enforced by review.
* A Service interface must live in `feature.[name].server.services` of the `:api` module
* A Service function must propagate errors via thrown exceptions; the return type only represents a successful result
    * **Why:** `@Throws` on a `suspend` function must include `CancellationException` (or a superclass such as `Exception`); without it, kotlinc rejects the function on iOS targets.
    * **Note:** Known service exceptions should be their own `@Serializable` type (ideally a `PresentableException`).
    * **Note:** `@Throws` on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException`.

##### Examples

A `@Urpc` service contract in `:api`, with nested `@Serializable` `Request`/`Response` types grouped under per-function `object` namespaces:

```kotlin
// feature.user.server.services.UserService.kt (:api)
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

## [Service Impl](../src/main/kotlin/architecture/rules/serverservices/ServiceImpl.kt)

An implementation of a `Service` interface (see [Service Interface](#service-interface)). A
ServiceImpl lives in `feature.[name].server.services` of `:server`, the same package as the
contract, so it belongs to this layer, not the top-level feature group.

##### Requirements

* A Service Impl resides in `feature..server.services..`
* A Service Impl is named `[Name]ServiceImpl`, matching its `[Name]Service` contract
* A Service Impl resides in `feature.[name].server.services` itself, beside the contract, not in a sub-package
* A Service Impl is declared in a `:server` module

##### Rules

* A Service implementation must be `internal`
* A Service implementation must implement the `@Urpc` contract its name pairs with: `[Name]ServiceImpl` implements `[Name]Service`
    * **Why:** The name is a claim — `[Name]ServiceImpl` says this class answers the `[Name]Service` contract — and a class that makes the claim without implementing the interface is either unfinished or misnamed. Nothing else would catch it: the shape classifies on name and package alone, so without this rule a contract-less impl passes every test while the service it names returns 404.
    * **Note:** The parent reference is resolved through the file's imports; the contract usually needs no import at all — it shares the impl's package from `:api` — so a same-package reference resolves to it directly.
* A Service implementation must not inject persistence: neither a Repository nor a StorageClass
    * **Why:** A ServiceImpl answers a request by composing the feature's `server.domain` interfaces; persistence sits on the far side of those interfaces, where `ServerServices.noDataImports` keeps it. A Repository is the wiring that provides those interfaces, not a thing to hold — injecting it, or the StorageClass under it, states the table the handler wants instead of the contract it needs, so nothing else can reuse that access and nothing names what the request actually required. It is the same rule that keeps ViewModels off client Repositories.
    * **Note:** Tested on the primary constructor: a parameter whose type is named `[Name]Repository`, `[Name]Storage`, or `[Name]Store`, or whose type resolves into a persistence package.
    * **Note:** This is the constructor-shaped half of `ServerServices.noDataImports`, which measures the same coupling over imports; a ServiceImpl reaching persistence some other way is counted there.
    * **Note:** Constructor discipline beyond persistence comes from the layer rules, not from a whitelist here: session authentication, domain interfaces, and platform types are all legitimate parameters.
* A Service implementation must not depend on the `ui` package
    * **Why:** ServiceImpls run on the server and have no Compose runtime: a UI import here would either fail to compile in `:server` or mean a UI type is being treated as data, both of which are wrong. If you need a shape shared with the UI, put it in the feature's `:api` domain or services package.

##### Guidance

* A Service implementation may inject its feature's `server.domain` interfaces, and other features' `server.domain` interfaces published to `:api`
    * **Note:** Another feature's Service contract is not on that list: it is the client's door, and injecting it is `ServerServices.noForeignServiceContractInjection`.
