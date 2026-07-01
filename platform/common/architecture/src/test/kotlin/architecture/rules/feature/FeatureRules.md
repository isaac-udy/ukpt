# Feature wiring (top-level package & DI)

The top-level `feature.[name]` package (in `:client` and `:server`) is reserved for dependency-injection wiring: Koin modules that define the feature's DI bindings, wiring its [ViewModels](ui.md#viewmodels), [Repositories](data.md#repositories), [UseCases](domain.md#usecases), and [Service](services.md#services-the-cross-the-wire-contract) implementations into the graph. Concrete classes (ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.

## Layer rules

These apply across every file in the top-level `feature.[name]` package:

{{rules:FeatureRules}}

## Dependency modules

* **Definition**: The configuration for Dependency Injection (DI) that wires the feature together.
{{construct:FeatureRules.DependencyModule}}
* **Note**: The naming convention is `[name]ClientDependencies` in `:client` and `[name]ServerDependencies` in `:server` — the construct enforces the `Dependencies` suffix; the `Client`/`Server` infix is convention.
* **Example** (registering a urpc service in `:server`, per `FeatureRules.DependencyModule.urpcServiceBinding`):
```kotlin
scope<UrpcCall> {
    scopedOf(::UserProfileServiceImpl)
        .bind(UserProfileService::class)
        .bindService(::UserProfileServiceUrpcBinding)
}
```
* **Note**: It is the responsibility of `:app` level modules (application shells) to collect all of the DI modules provided by feature modules and create the final dependency graph. When a new dependency module is added, it must be registered in both `:app:client:shared` and `:app:server`; when a new Service is added, it must be registered in `:app:server`.
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

## DI registration helpers

* **Definition**: An `internal` function with a Koin `Module` receiver that a `Dependencies` module calls to register a group of bindings — used to split a large module into readable, named chunks.
{{construct:FeatureRules.DependencyModuleHelper}}
