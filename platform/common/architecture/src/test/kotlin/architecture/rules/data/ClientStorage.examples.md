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
