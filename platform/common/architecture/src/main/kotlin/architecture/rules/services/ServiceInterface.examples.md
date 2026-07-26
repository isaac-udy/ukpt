> **Illustrative.** `:feature:core` defines no `@Urpc` contract in `:api` (and its `:server` is an empty stub). The contract below shows the shape; add a real one with the `ukpt-urpc-service` skill.

A `@Urpc` service contract in `:api`, with nested `@Serializable` `Request`/`Response` types grouped under per-function `object` namespaces:

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
