package feature.ukpt.server.web

import dev.isaacudy.udytils.htmx.OobList
import dev.isaacudy.udytils.htmx.oobUpdates
import feature.ukpt.Greeting
import feature.ukpt.server.domain.FlowOfGreetingSummary
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The whole list as the first event, then each change to it, rendered as out-of-band swaps. */
internal fun greetingEvents(flowOfGreetingSummary: FlowOfGreetingSummary): Flow<ServerSentEvent> {
    val list = OobList.unorderedList<Greeting, Long>(UkptIds.greetings, key = { it.id }) { greetingItem(it) }
    return flowOfGreetingSummary()
        .map { it.greetings }
        .oobUpdates(list)
        .map { ServerSentEvent(data = it, event = UkptIds.GREETINGS_EVENT) }
}
