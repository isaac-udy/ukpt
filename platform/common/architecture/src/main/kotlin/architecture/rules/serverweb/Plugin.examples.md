A Plugin installing session authentication that a domain interface checks:

```kotlin
internal class SessionPlugin(
    private val findSession: FindSession,
) : ServerPlugin {

    override fun Application.install() {
        install(Authentication) {
            session<AccountSession>(OrdersAuth.SESSION) {
                validate { session -> findSession(session.id)?.let(::AccountPrincipal) }
                challenge { call.respondRedirect(AccountPaths.SIGN_IN) }
            }
        }
    }
}
```

The dependency module binds it:

```kotlin
singleOf(::SessionPlugin) bind ServerPlugin::class
```
