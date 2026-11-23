package platform.testing.subcutaneous

import dev.isaacudy.udytils.state.ViewModelState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Suspends until the state matches [predicate] and returns it; fails after [timeout] of real time. */
suspend fun <S> ViewModelState<S>.awaitMatching(
    timeout: Duration = 10.seconds,
    predicate: (S) -> Boolean,
): S = withTimeoutOrNull(timeout) { asStateFlow().first(predicate) }
    ?: throw AssertionError("ViewModel state did not match within $timeout. Last state: $value")
