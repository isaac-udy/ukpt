package feature.ukpt.client.data

import dev.isaacudy.udytils.state.RepositoryState
import dev.isaacudy.udytils.state.repositoryState
import feature.ukpt.Greeting
import feature.ukpt.client.domain.FlowOfGreetingHistory
import feature.ukpt.client.domain.FlowOfLatestGreeting
import feature.ukpt.client.domain.GetGreeting
import feature.ukpt.client.domain.ResetGreetings
import kotlinx.coroutines.flow.map

internal class GreetingRepository {

    private val greetings: RepositoryState<GreetingRepository, List<Greeting>> =
        repositoryState(emptyList())

    val flowOfLatestGreeting = FlowOfLatestGreeting {
        greetings.map { it.lastOrNull() }
    }

    val flowOfGreetingHistory = FlowOfGreetingHistory {
        greetings
    }

    val getGreeting = GetGreeting {
        val text = "Hello"
        greetings.update { this + Greeting(text = text) }
        text
    }

    val resetGreetings = ResetGreetings {
        greetings.update { emptyList() }
    }
}
