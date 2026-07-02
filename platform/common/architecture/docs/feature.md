> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/feature/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Feature Rules](../src/main/kotlin/architecture/rules/feature/FeatureRules.kt)

The top-level `feature.[name]` package (in `:client` and `:server`) is reserved for
dependency-injection wiring: Koin modules that define the feature's DI bindings, wiring its
[ViewModels](ui.md#view-model), [Repositories](data.md#repository), [UseCases](domain.md#use-case),
and [Service](services.md#service-interface) implementations into the graph. Concrete classes
(ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.

##### Constructs

* [Dependency Module](#dependency-module)
* [Dependency Module Helper](#dependency-module-helper)

##### Rules

* A DI binding must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`
    * **Why:** The reference style lets Koin validate the constructor parameters against the graph at startup; the lambda style hides missing or cyclic dependencies until the first injection at runtime.

---

## [Dependency Module](../src/main/kotlin/architecture/rules/feature/DependencyModule.kt)

The configuration for Dependency Injection (DI) that wires the feature together.

* **Note:** The naming convention is `[name]ClientDependencies` in `:client` and
  `[name]ServerDependencies` in `:server`. The Construct enforces the `Dependencies` suffix;
  the `Client`/`Server` infix is convention.
* **Note:** The `:app` modules (application shells) are responsible for collecting the DI
  modules provided by feature modules into the final dependency graph. When a new dependency
  module is added, it must be registered in both `:app:client:shared` and `:app:server`; when
  a new Service is added, it must be registered in `:app:server`.

##### Requirements

* A Dependency Module resides in the top-level `feature.[name]` package of a `:client` or `:server` module
* A Dependency Module is a property
* A Dependency Module is named `[Name]Dependencies`

##### Rules

* A Dependency Module must only bind/provide dependencies that are both defined and implemented in its own feature
    * **Why:** If feature A binds an implementation of feature B's domain interface, feature B's DI graph silently depends on feature A, and removing or refactoring A breaks B's wiring at runtime rather than at compile time. Each feature owns its own bindings; cross-feature consumption goes through `:api` interfaces only.
* A Dependency Module registers a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block
    * **Note:** `bindService` (from `dev.isaacudy.udytils.urpc.koin`) registers the binding under its own concrete type, bound to `UrpcService`, with the impl resolved lazily.
    * **Note:** Never use `scoped<UrpcService> { [Name]ServiceUrpcBinding { get() } }`: every such binding shares the `UrpcService` definition key, so co-registered services override each other and `getAll<UrpcService>()` returns only one. The test catches this form.
    * **Note:** `urpcService(::[Name]ServiceUrpcBinding)` is the equivalent standalone form when there is no impl definition to chain off.

##### Examples

Registering a urpc service in `:server`, per `FeatureRules.DependencyModule.urpcServiceBinding`:

```kotlin
scope<UrpcCall> {
    scopedOf(::UserProfileServiceImpl)
        .bind(UserProfileService::class)
        .bindService(::UserProfileServiceUrpcBinding)
}
```

The kind of concrete class the server DI module binds. A `ServicesLayer.ServiceImpl` lives in `feature.[name].services`, never in the top-level package; only its binding appears here:

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

---

## [Dependency Module Helper](../src/main/kotlin/architecture/rules/feature/DependencyModuleHelper.kt)

An `internal` function with a Koin `Module` receiver that a `Dependencies` module calls to
register a group of bindings. Used to split a large module into readable, named chunks.

##### Requirements

* A Dependency Module Helper is a function
* A Dependency Module Helper is `internal`
* A Dependency Module Helper has a Koin `Module` receiver
