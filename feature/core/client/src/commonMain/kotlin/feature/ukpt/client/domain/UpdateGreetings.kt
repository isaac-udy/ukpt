package feature.ukpt.client.domain

fun interface UpdateGreetings {
    suspend operator fun invoke(update: Update)

    suspend fun reset() = invoke(Update.Reset)

    sealed interface Update {
        data object Reset : Update
    }
}
