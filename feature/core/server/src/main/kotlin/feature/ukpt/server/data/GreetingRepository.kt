package feature.ukpt.server.data

import dev.isaacudy.udytils.state.RepositoryState
import dev.isaacudy.udytils.state.repositoryState
import feature.ukpt.Greeting
import feature.ukpt.server.domain.FlowOfGreetingSummary
import feature.ukpt.server.domain.GreetingSummary
import feature.ukpt.server.domain.UpdateGreetings
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
                is UpdateGreetings.Update.Add -> this + Greeting(id = (maxOfOrNull { it.id } ?: 0) + 1, text = update.text)
                is UpdateGreetings.Update.Remove -> filterNot { it.id == update.id }
                UpdateGreetings.Update.Reset -> emptyList()
            }
        }
    }
}
