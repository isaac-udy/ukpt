Example for `ProjectRules.noBackingProperties`:

```kotlin
// Good
class CartStore {
    val items: StateFlow<List<Item>>
        field = MutableStateFlow(emptyList())

    fun add(item: Item) {
        items.value = items.value + item
    }
}

// Avoid
class CartStore {
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> get() = _items

    fun add(item: Item) {
        _items.value = _items.value + item
    }
}
```

A value the class reassigns is a `var` with a `private set`; an explicit backing field cannot be reassigned:

```kotlin
// Good
class CartStore {
    var lastSyncedAt: Instant? = null
        private set
}

// Avoid
class CartStore {
    private var _lastSyncedAt: Instant? = null
    val lastSyncedAt: Instant? get() = _lastSyncedAt
}
```

Example for `ProjectRules.sealedActionVariants`:

```kotlin
// Good
sealed interface UserAction {
    data class Rename(val id: User.Id, val newName: String) : UserAction
    data class Delete(val id: User.Id) : UserAction
}

// Avoid
enum class ActionType { RENAME, DELETE }
data class UserActionRequest(val id: User.Id, val type: ActionType, val newName: String? = null)
```
