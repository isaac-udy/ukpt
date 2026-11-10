package feature.sample.server.web

import dev.isaacudy.udytils.htmx.OobList
import dev.isaacudy.udytils.htmx.oobUpdates
import feature.sample.server.domain.FlowOfSamples
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun sampleEvents(flowOfSamples: FlowOfSamples): Flow<ServerSentEvent> {
    val list = OobList.unorderedList<String, String>(SampleIds.samples, key = { it }) { sampleItem(it) }
    return flowOfSamples()
        .oobUpdates(list)
        .map { ServerSentEvent(data = it, event = SampleIds.SAMPLES_EVENT) }
}
