package feature.ukpt.server.web

internal object UkptPaths {
    const val HOME = "/"
    const val GREETINGS = "/greetings"
    const val GREETING_EVENTS = "/greetings/events"
    const val DELETE_GREETING = "/greetings/{id}/delete"
    const val GREETING_NAME_SCRIPT = "/static/ukpt/greeting-name.js"

    fun deleteGreeting(id: Long): String = "$GREETINGS/$id/delete"
}
