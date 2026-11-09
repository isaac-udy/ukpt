package feature.ukpt.tests

import feature.ukpt.Greeting
import feature.ukpt.server.domain.FlowOfGreetingHistory
import feature.ukpt.server.domain.UpdateGreetingHistory
import kotlinx.coroutines.flow.first
import platform.testing.subcutaneous.Scenario

/** Adds a greeting for each of [texts], in order, and returns the whole history. */
suspend fun Scenario.greetingHistory(vararg texts: String): List<Greeting> {
    val updateGreetingHistory = server<UpdateGreetingHistory>()
    texts.forEach { updateGreetingHistory.add(it) }
    return server<FlowOfGreetingHistory>()().first()
}
