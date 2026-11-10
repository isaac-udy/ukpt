package feature.sample.server.web

import dev.isaacudy.udytils.htmx.elementId
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.UL
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.li
import kotlinx.html.ul
import platform.server.web.LayoutState
import platform.server.web.textField
import platform.server.web.ukptLayout

internal fun HTML.samplePage(state: SamplePageState) {
    ukptLayout(LayoutState(title = "Samples")) {
        sampleForm(state.form, state.errors)
        ul("list") {
            elementId = SampleIds.samples
            state.labels.forEach { sampleItem(it) }
        }
    }
}

internal fun FlowContent.sampleForm(form: SampleForm, errors: SampleForm.Errors?) {
    form(action = SamplePaths.SAMPLES, method = FormMethod.post, classes = "card") {
        elementId = SampleIds.sampleForm
        textField(name = "label", label = "Label", value = form.label, error = errors?.label)
        button(type = ButtonType.submit, classes = "button") { +"Add" }
    }
}

internal fun UL.sampleItem(label: String) {
    li("row") { +label }
}
