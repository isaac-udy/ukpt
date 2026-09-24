package feature.ukpt.server.web

import io.ktor.http.Parameters
import platform.server.web.FormResult

internal data class GreetingForm(
    val name: String,
) {
    fun validate(): FormResult<String, Errors> {
        val trimmed = name.trim()
        val nameError = when {
            trimmed.isEmpty() -> "Enter a name."
            trimmed.length > MAX_NAME_LENGTH -> "Use at most $MAX_NAME_LENGTH characters."
            else -> null
        }
        return if (nameError == null) FormResult.Valid(trimmed) else FormResult.Invalid(Errors(name = nameError))
    }

    data class Errors(val name: String?)

    companion object {
        const val MAX_NAME_LENGTH = 40

        val Empty = GreetingForm(name = "")

        fun from(parameters: Parameters): GreetingForm = GreetingForm(name = parameters["name"].orEmpty())
    }
}
