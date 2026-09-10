package feature.shop.client.ui

import dev.isaacudy.udytils.state.AsyncState

data class RefundState(
    val loading: Boolean = false,
    val error: String? = null,
    val refreshProgress: AsyncState<Unit> = AsyncState.Idle(),
) {
    val refreshing: Boolean get() = refreshProgress is AsyncState.Loading
}
