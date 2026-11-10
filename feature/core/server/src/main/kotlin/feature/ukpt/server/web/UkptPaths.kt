package feature.ukpt.server.web

object UkptPaths {
    const val HOME = "/"
    const val GREETINGS = "/greetings"
    const val GREETING_EVENTS = "/greetings/events"
    const val DELETE_GREETING = "/greetings/{id}/delete"
    const val GREETING_NAME_SCRIPT = "/static/ukpt/greeting-name.js"

    fun deleteGreeting(id: String): String = "$GREETINGS/$id/delete"
}
