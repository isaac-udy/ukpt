package feature.ukpt.server.domain

fun interface UpdateGreetings {
    suspend operator fun invoke(update: Update)

    suspend fun add(text: String) = invoke(Update.Add(text = text))

    suspend fun remove(id: Long) = invoke(Update.Remove(id = id))

    suspend fun reset() = invoke(Update.Reset)

    sealed interface Update {
        data class Add(val text: String) : Update
        data class Remove(val id: Long) : Update
        data object Reset : Update
    }
}
