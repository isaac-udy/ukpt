package feature.ukpt.client.data

import dev.isaacudy.udytils.state.RepositoryState
import dev.isaacudy.udytils.state.repositoryState
import feature.ukpt.Greeting
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.GreetingSummary
import feature.ukpt.client.domain.UpdateGreetings
import kotlinx.coroutines.flow.map

internal class GreetingRepository {

    private val greetings: RepositoryState<GreetingRepository, List<Greeting>> =
        repositoryState(emptyList())

    val flowOfGreetingSummary = FlowOfGreetingSummary {
        greetings.map { GreetingSummary(greetings = it) }
    }

    val updateGreetings = UpdateGreetings { update ->
        greetings.update {
            when (update) {
                is UpdateGreetings.Update.Add -> this + Greeting(text = update.text)
                UpdateGreetings.Update.Reset -> emptyList()
            }
        }
    }
}
