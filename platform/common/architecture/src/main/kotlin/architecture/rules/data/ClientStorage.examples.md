> **Illustrative.** `:feature:core` ships no client storage class. The `expect`/`actual` shape below is the pattern for a platform-backed store when a feature needs one.

An `expect`/`actual` Storage class with a platform-specific backing store:

```kotlin
// commonMain
expect class AuthCredentialStorage() {
    val authCredentials: StateFlow<AuthCredentials?>
    fun setAuthCredentials(authCredentials: AuthCredentials?)
}

// androidMain
actual class AuthCredentialStorage actual constructor() {
    // Android-specific implementation using SharedPreferences/DataStore
}
```
