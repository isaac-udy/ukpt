Domain objects showing a nested value-class ID, an `init` invariant, and a sealed hierarchy with nested types:

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
    @SerialName("Transport.Car")
    data class Car(
        override val id: String,
        override val name: String,
        val fuelType: FuelType,
    ) : Transport {
        @Serializable
        enum class FuelType {
            Petrol,
            Diesel,
            Electric,
            Hydrogen,
        }
    }

    @Serializable
    @SerialName("Transport.Bicycle")
    data class Bicycle(
        override val id: String,
        override val name: String,
        val type: Type,
    ) : Transport {
        @Serializable
        enum class Type {
            Manual,
            Electric,
        }
    }

    @Serializable
    @SerialName("Transport.Bus")
    data class Bus(
        override val id: String,
        override val name: String,
        val routeId: String,
    ) : Transport
}
```
