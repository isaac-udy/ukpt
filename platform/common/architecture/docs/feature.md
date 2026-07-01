> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Narrative sources: the `FeatureRules*.md` fragments in `src/test/kotlin/architecture/rules/feature/`; structure and rule content come from the rule catalog.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

# Feature wiring (top-level package & DI)

The top-level `feature.[name]` package (in `:client` and `:server`) is reserved for dependency-injection wiring: Koin modules that define the feature's DI bindings, wiring its [ViewModels](ui.md#viewmodels), [Repositories](data.md#repositories), [UseCases](domain.md#usecases), and [Service](services.md#services-the-cross-the-wire-contract) implementations into the graph. Concrete classes (ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.

## Layer rules

* **`FeatureRules.constructorReferenceBindings`** `✅ tested` — DI bindings must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`
    * **Why**: The reference style lets Koin validate the constructor parameters against the graph at startup; the lambda style hides missing or cyclic dependencies until the first injection at runtime.

## Dependency modules

* **Definition**: The configuration for Dependency Injection (DI) that wires the feature together.
* **Note**: The naming convention is `[name]ClientDependencies` in `:client` and `[name]ServerDependencies` in `:server` — the construct enforces the `Dependencies` suffix; the `Client`/`Server` infix is convention.
* **Note**: It is the responsibility of `:app` level modules (application shells) to collect all of the DI modules provided by feature modules and create the final dependency graph. When a new dependency module is added, it must be registered in both `:app:client:shared` and `:app:server`; when a new Service is added, it must be registered in `:app:server`.
* **Example** (registering a urpc service in `:server`, per `FeatureRules.DependencyModule.urpcServiceBinding`):
```kotlin
scope<UrpcCall> {
    scopedOf(::UserProfileServiceImpl)
        .bind(UserProfileService::class)
        .bindService(::UserProfileServiceUrpcBinding)
}
```
* **Example** — the kind of concrete class the server DI module binds. A `ServicesLayer.ServiceImpl` lives in `feature.[name].services`, never in the top-level package; only its binding appears here:
```kotlin
// feature.user.services.UserServiceImpl.kt (:server)
internal class UserServiceImpl(
    private val userStorage: UserStorage,
    private val sessionAuth: SessionAuth,
) : UserService {

    override suspend fun createUser(request: UserService.CreateUser.Request): UserService.CreateUser.Response {
        val userId = sessionAuth.requireUser().first()
        val user = userStorage.insertUser(
            row = Users.Row(name = request.name, email = request.email)
        )
        return UserService.CreateUser.Response(user = user)
    }

    override suspend fun getUser(request: UserService.GetUser.Request): UserService.GetUser.Response {
        val user = userStorage.getUser(request.userId)
        return UserService.GetUser.Response(user = user)
    }

    override fun observeUsers(): Flow<UserService.ObserveUsers.Response> =
        userStorage.observeAll()
            .map { UserService.ObserveUsers.Response(users = it) }
}
```

* **Construct** `FeatureRules.DependencyModule` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * DI modules must be defined in the top-level `feature.[name]` package of the `:client` and `:server` modules
    * is a property
    * name ends with `Dependencies`
* **Rules**:
    * **`FeatureRules.DependencyModule.ownFeatureBindingsOnly`** `✅ tested` — The DI module for a feature must only bind/provide dependencies that are both defined and implemented in that feature
        * **Why**: If feature A binds an implementation of feature B's domain interface, feature B's DI graph silently depends on feature A — and removing/refactoring A breaks B's wiring at runtime, not at compile time. Each feature owns its own bindings; cross-feature consumption goes through `:api` interfaces only.
    * **`FeatureRules.DependencyModule.urpcServiceBinding`** `📋 guidance` — Register a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block
        * **Note**: `bindService` (from `dev.isaacudy.udytils.urpc.koin`) registers the binding under its own concrete type, bound to `UrpcService`, with the impl resolved lazily.
        * **Note**: Do NOT use `scoped<UrpcService> { [Name]ServiceUrpcBinding { get() } }` — every such binding shares the `UrpcService` definition key, so co-registered services override each other and `getAll<UrpcService>()` returns only one.
        * **Note**: `urpcService(::[Name]ServiceUrpcBinding)` is the equivalent standalone form when there is no impl definition to chain off.

## DI registration helpers

* **Definition**: An `internal` function with a Koin `Module` receiver that a `Dependencies` module calls to register a group of bindings — used to split a large module into readable, named chunks.

* **Construct** `FeatureRules.DependencyModuleHelper` (`🔶 construct`) — a declaration is this construct when it satisfies all of:
    * is a function
    * is `internal`
    * A DI registration helper has a Koin `Module` receiver
