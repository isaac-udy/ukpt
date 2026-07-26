> **Illustrative.** `:feature:core`'s `:server` is an empty stub, so it registers no urpc service. The binding and `ServiceImpl` below show the shape; stand a real one up with the `ukpt-urpc-service` skill.

Registering a urpc service in `:server`, per `FeatureRules.DependencyModule.urpcServiceBinding`:

```kotlin
scope<UrpcCall> {
    scopedOf(::UserServiceImpl)
        .bind(UserService::class)
        .bindService(::UserServiceUrpcBinding)
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
