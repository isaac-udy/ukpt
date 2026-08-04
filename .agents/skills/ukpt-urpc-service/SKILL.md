---
name: ukpt-urpc-service
description: >-
  Add or change a urpc client/server service in UKPT end-to-end — the @Urpc
  contract in :api, the ServiceImpl + Koin binding on :server, and (for the very
  first service) the Ktor host. Use when adding an @Urpc service, wiring a
  client↔server call, or working with the `server.services` layer of a feature's
  :api/:server modules.
---

# ukpt-urpc-service

urpc is UKPT's typed client↔server contract. An `@Urpc` interface in `:feature:<name>:api` is
processed by KSP into a **client** (consumed by `:client` Repositories) and a **server binding**
(`<Name>ServiceUrpcBinding`); the `:server` module implements the interface and binds it into Koin;
the Ktor host serves every binding. `:feature:core` ships **no service yet**, so the *first* service
also stands up the host (Step 5). Copy-paste skeletons are in `templates.md`.

> **Package reality:** the module path is `:feature:core` but the Kotlin package is `feature.ukpt`.
> Services live in `feature.ukpt.server.services`. For a new feature `<name>`, use
> `feature.<name>.server.services` — the contract in `:api` and the implementation in `:server`
> share that package; the module split is what separates them.

## Steps

1. **Contract** (`:api`, `feature.<name>.server.services`). `@Urpc("<wire>") interface <Name>Service` with a
   per-function `object` namespace holding nested `@Serializable Request`/`Response` (templates.md §1).
   Function shapes — each takes 0 or 1 param: `suspend fun f(req): Res` (unary), `fun f(req): Flow<Res>`
   (server stream), `fun f(reqs: Flow<Req>): Flow<Res>` (bidi). Errors propagate as **thrown exceptions**;
   the return type only models success. **No `:api` build edit** — urpc KSP is already wired per-target.
2. **Generate** — run the compile sweep (see UKPT.md §Compiling) so KSP emits `<Name>ServiceUrpcBinding`
   + the client before Steps 3–4 reference them.
3. **Implement** (`:server`, `feature.<name>.server.services`): `internal class <Name>ServiceImpl(...) : <Name>Service`
   (the `ServerServices.ServiceImpl` construct). A ServiceImpl is an **entry point**: it answers the
   request and hands the work to `server.domain` — inject this feature's domain interfaces
   (`ServerServices.ServiceImpl.mayInjectDomainInterfaces`) and state a new one when the operation
   needs logic that doesn't exist yet. **Never persistence** — no `server.data` imports, no
   StorageClasses (`ServerServices.ServiceImpl.noPersistenceInjection`, `ServerServices.noDataImports`) —
   and **never another feature's Service contract**
   (`ServerServices.noForeignServiceContractInjection`): reach another server feature through the
   `server.domain` interface it publishes to `:api`. Persistence, when the feature truly stores data,
   sits behind those domain interfaces in `server.data` (that needs the `:platform:server:postgres`
   setup — flag it, don't auto-scaffold).
4. **Bind in DI** (`:server`, `feature.<name>`): a `<name>ServerDependencies` Koin module (templates.md §3):
   ```kotlin
   scope<UrpcCall> {
       scopedOf(::<Name>ServiceImpl).bind(<Name>Service::class).bindService(::<Name>ServiceUrpcBinding)
   }
   ```
   ⚠️ **404 trap — do NOT write `scoped<UrpcService> { <Name>ServiceUrpcBinding { get() } }`.** That keys every
   binding to the single `UrpcService` type, so registering a second service overwrites the first and the host's
   `getAll<UrpcService>()` returns only one → **every other service 404s**. `bindService`/`urpcService` register
   each binding under its own concrete type and add `UrpcService` as a *secondary* type, so all coexist.
   (`FeatureRules.constructorReferenceBindings`: use constructor-reference binding style, not `single<T> { … }` lambdas.)
5. **First service only — teach the host to serve urpc** (idempotent; skip if already wired):
   - `Server.kt` already has a Koin host (it wires the postgres modules), so add
     `<name>ServerDependencies` to the existing `modules(...)` list — that part is what every later
     feature repeats. What a *first* service adds is `install(WebSockets)` (**required even for unary
     calls**) and `routing { urpcWithKoin() }` in place of the placeholder route (templates.md §4).
   - `:app:server` is missing `implementation(libs.urpc.koin)`, which is **not** transitive from
     `:feature:*:server`; koin-core, koin-ktor and Ktor's WebSockets artifact are already there
     (templates.md §5).
6. **Errors** — service exceptions must be `@Serializable` (`ProjectRules.serviceExceptionsSerializable`) so urpc carries type + message to the
   client; prefer subclassing `PresentableException` with a deliberate `retryable` flag (templates.md §6). Never
   `catch (Exception)` — deserialized urpc errors may not extend `Exception`; use `catch (t: Throwable)` or a
   specific type (`ProjectRules.noCatchException`).
7. **Verify** — the full compile sweep (all 6 targets) + `./gradlew :platform:common:architecture:verifyArchitecture`.
   If a web client consumes the service, also run the `ukpt-verify-web` skill.

## Rule cheat-sheet (canonical text lives in `platform/common/architecture/docs/` — search the ID)
- **`ServerServices.ServiceImpl`** (construct) + **`ServerServices.ServiceImpl.internalVisibility`** — impl is `<Name>ServiceImpl`, `internal`, in `feature.<name>.server.services` on `:server`.
- **`ServerServices.ServiceImpl.mayInjectDomainInterfaces`** / **`ServerServices.ServiceImpl.noPersistenceInjection`** — impls consume `server.domain` interfaces; they never inject a StorageClass or anything from `server.data`.
- **`ServerServices.noDataImports`** / **`ServerServices.noForeignServiceContractInjection`** — the layer never imports persistence, and never injects another feature's Service contract; the published `server.domain` interface is the cross-feature channel.
- **`ProjectRules.subsystemVisibility`** — a `server.services` sub-package sees its own package, its direct children, and its ancestors — never a sibling.
- **`ServerData.StorageClass.returnsRowTypesOnly`** — storage classes take/return `XxxRow` only, never domain types.
- **`FeatureRules.DependencyModule`** (construct) + **`FeatureRules.constructorReferenceBindings`** + **`FeatureRules.DependencyModule.urpcServiceBinding`** — DI is a `val <name>ServerDependencies` module in `feature.<name>`; constructor-ref bindings; the `scope<UrpcCall> { … bindService(…) }` form above.
- **`ProjectRules.noCatchException`** / **`ProjectRules.serviceExceptionsSerializable`** — no `catch (Exception)`; `@Serializable` service exceptions.

## Reference — real, compiling examples (read, don't copy-into-the-repo)
- Contract shape + the 3 function forms — `embedded-udytils/urpc/sample/src/main/kotlin/dev/isaacudy/udytils/urpc/sample/ExampleService.kt`
- Binding API + the 404-trap rationale (KDoc) — `embedded-udytils/urpc/koin/src/main/kotlin/dev/isaacudy/udytils/urpc/koin/UrpcServiceBinding.kt`
- Host dispatch — `urpcWithKoin` + the `UrpcCall` scope — `embedded-udytils/urpc/koin/src/main/kotlin/dev/isaacudy/udytils/urpc/koin/UrpcKoin.kt`
- End-to-end host + binding, incl. the multi-service regression guard — `embedded-udytils/urpc/sample/src/test/kotlin/dev/isaacudy/udytils/urpc/sample/ExampleServiceWithKoinTest.kt`
- `@Urpc` / `@UrpcWireName` — `embedded-udytils/urpc/protocol/src/commonMain/kotlin/dev/isaacudy/udytils/urpc/Urpc.kt`
- Copy-paste skeletons — `templates.md` (this skill).
