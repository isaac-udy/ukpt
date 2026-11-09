package feature.ukpt.server.domain

fun interface UpdateGreetingHistory {
    suspend operator fun invoke(update: Update)

    suspend fun add(text: String) = invoke(Update.Add(text = text))

    suspend fun reset() = invoke(Update.Reset)

    sealed interface Update {
        data class Add(val text: String) : Update
        data object Reset : Update
    }
}
