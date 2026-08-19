Registering a urpc service in `:server`, per `FeatureRules.DependencyModule.urpcServiceBinding`:

```kotlin
scope<UrpcCall> {
    scopedOf(::UserProfileServiceImpl)
        .bind(UserProfileService::class)
        .bindService(::UserProfileServiceUrpcBinding)
}
```

Service implementations live in `feature.[name].server.services`, never in the top-level package; only their bindings appear in the dependency module:

```kotlin
// feature.user.server.services.UserServiceImpl.kt (:server)
internal class UserServiceImpl(
    private val createUser: CreateUser,
    private val getUser: GetUser,
) : UserService { /* … */ }
```
