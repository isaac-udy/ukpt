package platform.server.web

import kotlinx.html.FlowContent
import kotlinx.html.INPUT
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p

/**
 * A labelled text input. When [error] is set, it is rendered below the input and announced as the
 * input's description.
 */
fun FlowContent.textField(
    name: String,
    label: String,
    value: String,
    error: String?,
    configure: INPUT.() -> Unit = {},
) {
    val inputId = "field-$name"
    val errorId = "$inputId-error"
    div("field") {
        label {
            htmlFor = inputId
            +label
        }
        input(type = InputType.text, name = name) {
            id = inputId
            this.value = value
            if (error != null) {
                attributes["aria-invalid"] = "true"
                attributes["aria-describedby"] = errorId
            }
            configure()
        }
        if (error != null) {
            p("field-error") {
                id = errorId
                +error
            }
        }
    }
}
