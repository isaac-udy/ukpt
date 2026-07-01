# Services (the cross-the-wire contract)

The client-server contract (in `:api`) and its implementation (in `:server`). Services use **urpc** (`dev.isaacudy.udytils:urpc-*`): KSP generates the client, the `UrpcService` server binding, and the wire descriptors from the annotated interface.
* **Note**: Service-level exception conventions — dedicated `@Serializable` exception types, `PresentableException`, and the deliberate `retryable` flag — are covered in [exception handling](exceptions.md).
* **Example**:
```kotlin
// feature.user.services.UserService.kt (:api)
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
