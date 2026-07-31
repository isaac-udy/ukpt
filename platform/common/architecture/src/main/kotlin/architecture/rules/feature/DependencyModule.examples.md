Registering a urpc service in `:server`, per `FeatureRules.DependencyModule.urpcServiceBinding`:

```kotlin
scope<UrpcCall> {
    scopedOf(::UserProfileServiceImpl)
        .bind(UserProfileService::class)
        .bindService(::UserProfileServiceUrpcBinding)
}
```

The kind of concrete class the server DI module binds. A `ServerServices.ServiceImpl` lives in `feature.[name].server.services`, never in the top-level package; only its binding appears here:

```kotlin
// feature.user.server.services.UserServiceImpl.kt (:server)
internal class UserServiceImpl(
    private val createUser: CreateUser,
    private val getUser: GetUser,
    private val flowOfUsers: FlowOfUsers,
    private val sessionAuth: SessionAuth,
) : UserService {

    override suspend fun createUser(request: UserService.CreateUser.Request): UserService.CreateUser.Response {
        sessionAuth.requireUser().first()
        val user = createUser(name = request.name, email = request.email)
        return UserService.CreateUser.Response(user = user)
    }

    override suspend fun getUser(request: UserService.GetUser.Request): UserService.GetUser.Response {
        val user = getUser(request.userId)
        return UserService.GetUser.Response(user = user)
    }

    override fun observeUsers(): Flow<UserService.ObserveUsers.Response> =
        flowOfUsers.allUsers()
            .map { UserService.ObserveUsers.Response(users = it) }
}
```
