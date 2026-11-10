package feature.sample.server.web

import feature.sample.server.domain.AddSample
import feature.sample.server.domain.FlowOfSamples
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sse.sse
import kotlinx.coroutines.flow.first
import platform.server.web.FormResult
import platform.server.web.WebRoutes
import platform.server.web.respondSeeOther

internal class SampleRoutes(
    private val flowOfSamples: FlowOfSamples,
    private val addSample: AddSample,
) : WebRoutes {

    override fun Route.install() {
        get(SamplePaths.HOME) {
            call.respondHtml { samplePage(SamplePageState(flowOfSamples().first(), SampleForm.Empty, errors = null)) }
        }

        get(SamplePaths.ARCHIVE) {
            call.respondHtml { archivePage(SamplePageState(flowOfSamples().first(), SampleForm.Empty, errors = null)) }
        }

        post(SamplePaths.SAMPLES) {
            val form = SampleForm.from(call.receiveParameters())
            when (val result = form.validate()) {
                is FormResult.Invalid -> call.respondHtml { samplePage(SamplePageState(flowOfSamples().first(), form, result.errors)) }
                is FormResult.Valid -> {
                    addSample(result.value)
                    call.respondSeeOther(SamplePaths.HOME)
                }
            }
        }

        sse(SamplePaths.SAMPLE_EVENTS) {
            sampleEvents(flowOfSamples).collect { send(it) }
        }
    }
}
