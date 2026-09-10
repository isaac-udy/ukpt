package feature.shop.client.ui

import dev.isaacudy.udytils.state.AsyncState

data class SaveState(
    val saving: Boolean = false,
    val deleteProgress: AsyncState<Unit> = AsyncState.Idle(),
) {
    val deleting: Boolean get() = deleteProgress is AsyncState.Loading
}
