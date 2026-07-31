A Repository providing two domain interfaces over a table its StorageClass owns, mapping Rows on the way out:

```kotlin
internal class UsersRepository(
    private val userStorage: UserStorage,
    private val userRoleStorage: UserRoleStorage,
) {
    val getUser = GetUser { id ->
        userStorage.getById(id)?.toDomain()
    }

    val flowOfUsersForTeam = FlowOfUsersForTeam { teamId ->
        userStorage.observeForTeam(teamId).map { rows ->
            rows.map { it.toDomain() }
        }
    }
}
```

A domain object that spans two tables is composed here, not in either StorageClass:

```kotlin
val getUserWithRoles = GetUserWithRoles { id ->
    val user = userStorage.getById(id) ?: return@GetUserWithRoles null
    val roles = userRoleStorage.listForUser(id)
    user.toDomain(roles = roles.map { it.toDomain() })
}
```
