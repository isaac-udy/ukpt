# Dependency modules

The configuration for Dependency Injection (DI) that wires the feature together.
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
