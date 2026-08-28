package feature.ukpt.client.ui

import dev.isaacudy.udytils.state.AsyncState
import feature.ukpt.client.domain.GreetingSummary

data class UkptState(
    val greetingSummary: AsyncState<GreetingSummary> = AsyncState.Idle(),
    val greetAction: AsyncState<Unit> = AsyncState.Idle(),
)
