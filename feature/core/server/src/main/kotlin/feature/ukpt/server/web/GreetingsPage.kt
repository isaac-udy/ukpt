package feature.ukpt.server.web

import dev.isaacudy.udytils.htmx.HxTarget
import dev.isaacudy.udytils.htmx.SwapStyle
import dev.isaacudy.udytils.htmx.alpine
import dev.isaacudy.udytils.htmx.elementId
import dev.isaacudy.udytils.htmx.hx
import dev.isaacudy.udytils.htmx.sse
import feature.ukpt.Greeting
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.UL
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.ul
import platform.server.web.LayoutState
import platform.server.web.textField
import platform.server.web.ukptLayout

internal fun HTML.greetingsPage(state: GreetingsPageState) {
    ukptLayout(LayoutState(title = "Greetings", scripts = listOf(UkptPaths.GREETING_NAME_SCRIPT))) {
        h1 { +"Greetings" }
        greetingForm(state.form, state.errors)
        greetingList(state.greetings)
    }
}

/**
 * Posts as a plain form without JavaScript; with htmx, the response replaces the form, and the
 * new greeting reaches the list over the greetings event stream.
 */
internal fun FlowContent.greetingForm(form: GreetingForm, errors: GreetingForm.Errors?) {
    form(action = UkptPaths.GREETINGS, method = FormMethod.post, classes = "card") {
        elementId = UkptIds.greetingForm
        hx {
            post(UkptPaths.GREETINGS)
            target(HxTarget.This)
            swap(SwapStyle.OuterHtml)
            disabledElement(HxTarget.find("button"))
        }
        div {
            alpine {
                data("greetingName")
                on("input", "update")
            }
            textField(name = "name", label = "Name", value = form.name, error = errors?.name) {
                attributes["maxlength"] = GreetingForm.MAX_NAME_LENGTH.toString()
                attributes["autocomplete"] = "off"
            }
            p("muted") {
                span { alpine { text("remaining") } }
            }
        }
        button(type = ButtonType.submit, classes = "button") { +"Greet" }
    }
}

internal fun FlowContent.greetingList(greetings: List<Greeting>) {
    section("card") {
        h2 { +"Everyone greeted so far" }
        ul("list") {
            elementId = UkptIds.greetings
            attributes["data-empty-text"] = "No greetings yet."
            greetings.forEach { greetingItem(it) }
        }
        div {
            sse {
                connect(UkptPaths.GREETING_EVENTS)
                swap(UkptIds.GREETINGS_EVENT)
            }
            hx { swap(SwapStyle.None) }
        }
    }
}

internal fun UL.greetingItem(greeting: Greeting) {
    li("row") {
        elementId = UkptIds.greeting(greeting.id)
        span("grow") { +greeting.text }
        form(action = UkptPaths.deleteGreeting(greeting.id), method = FormMethod.post) {
            hx {
                post(UkptPaths.deleteGreeting(greeting.id))
                swap(SwapStyle.None)
            }
            button(type = ButtonType.submit, classes = "button secondary") { +"Remove" }
        }
    }
}
