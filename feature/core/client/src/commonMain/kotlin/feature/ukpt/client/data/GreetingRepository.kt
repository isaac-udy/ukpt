package feature.ukpt.client.data

import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.Greet
import feature.ukpt.client.domain.GreetingSummary
import feature.ukpt.client.domain.UpdateGreetings
import feature.ukpt.server.services.GreetingService
import kotlinx.coroutines.flow.map

internal class GreetingRepository(
    private val greetingService: GreetingService,
) {

    val flowOfGreetingSummary = FlowOfGreetingSummary {
        greetingService.observeHistory().map { GreetingSummary(greetings = it.greetings) }
    }

    val greet = Greet {
        greetingService.greet()
    }

    val updateGreetings = UpdateGreetings { update ->
        when (update) {
            UpdateGreetings.Update.Reset -> greetingService.reset()
        }
    }
}
