# Domain objects

* **Definition**: An immutable type representing data at the domain-level.
* **Note**: Nested types (enums, value classes, sealed interfaces/classes) belong nested only when conceptually inseparable from the parent — like `User.Id` or `Transport.Car.FuelType` below; otherwise model them as their own domain objects.
* **Examples**:
```kotlin
@Serializable
data class User(
    val id: Id,
    val name: String,
    val friends: List<Id>,
) {
    @Serializable
    @JvmInline
    value class Id(val value: String)
}

@Serializable
data class UserAndFriends(
    val user: User,
    val friends: List<User>,
) {
    init {
        require(friends.all { friend -> user.friends.contains(friend.id) }) {
            "All users in friends must have an id matching a value in user.friends"
        }
    }
}

@Serializable
sealed interface Transport {
    val id: String
    val name: String

    @Serializable
    data class Car(
        override val id: String,
        override val name: String,
        val fuelType: FuelType,
    ) {
        @Serializable
        enum class FuelType {
            Petrol,
            Diesel,
            Electric,
            Hydrogen,
        }
    }

    @Serializable
    data class Bicycle(
        override val id: String,
        override val name: String,
        val type: Type,
    ) {
        @Serializable
        enum class Type {
            Manual,
            Electric,
        }
    }

    @Serializable
    data class Bus(
        override val id: String,
        override val name: String,
        val routeId: String,
    )
}
```
