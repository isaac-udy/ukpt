package feature.ukpt.server.web

import dev.isaacudy.udytils.htmx.isHtmx
import dev.isaacudy.udytils.htmx.respondHtmlFragment
import dev.isaacudy.udytils.htmx.varyOnHtmx
import feature.ukpt.server.domain.FlowOfGreetingSummary
import feature.ukpt.server.domain.Greet
import feature.ukpt.server.domain.UpdateGreetings
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sse.sse
import kotlinx.coroutines.flow.first
import platform.server.web.FormResult
import platform.server.web.WebRoutes
import platform.server.web.respondSeeOther

internal class UkptRoutes(
    private val flowOfGreetingSummary: FlowOfGreetingSummary,
    private val greet: Greet,
    private val updateGreetings: UpdateGreetings,
) : WebRoutes {

    override fun Route.install() {
        get(UkptPaths.HOME) {
            val greetings = flowOfGreetingSummary().first().greetings
            call.respondHtml { greetingsPage(GreetingsPageState(greetings, GreetingForm.Empty, errors = null)) }
        }

        post(UkptPaths.GREETINGS) {
            val form = GreetingForm.from(call.receiveParameters())
            call.varyOnHtmx()
            when (val result = form.validate()) {
                is FormResult.Invalid -> if (call.isHtmx) {
                    call.respondHtmlFragment(HttpStatusCode.UnprocessableEntity) { greetingForm(form, result.errors) }
                } else {
                    val greetings = flowOfGreetingSummary().first().greetings
                    call.respondHtml(HttpStatusCode.UnprocessableEntity) {
                        greetingsPage(GreetingsPageState(greetings, form, result.errors))
                    }
                }
                is FormResult.Valid -> {
                    greet(result.value)
                    if (call.isHtmx) {
                        call.respondHtmlFragment { greetingForm(GreetingForm.Empty, errors = null) }
                    } else {
                        call.respondSeeOther(UkptPaths.HOME)
                    }
                }
            }
        }

        post(UkptPaths.DELETE_GREETING) {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            updateGreetings.remove(id)
            if (call.isHtmx) call.respond(HttpStatusCode.NoContent) else call.respondSeeOther(UkptPaths.HOME)
        }

        sse(UkptPaths.GREETING_EVENTS) {
            greetingEvents(flowOfGreetingSummary).collect { send(it) }
        }
    }
}
