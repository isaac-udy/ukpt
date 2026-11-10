package feature.sample.server.web

import io.ktor.http.Parameters
import platform.server.web.FormResult

internal data class SampleForm(
    val label: String,
) {
    fun validate(): FormResult<String, Errors> {
        val trimmed = label.trim()
        return if (trimmed.isEmpty()) FormResult.Invalid(Errors(label = "Enter a label.")) else FormResult.Valid(trimmed)
    }

    data class Errors(val label: String?)

    companion object {
        val Empty = SampleForm(label = "")

        fun from(parameters: Parameters): SampleForm = SampleForm(label = parameters["label"].orEmpty())
    }
}
