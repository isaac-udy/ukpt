package platform.testing.subcutaneous

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Retries [assertion] until it passes and returns its result; rethrows its last failure after
 * [timeout] of real time. For navigation a ViewModel performs after a state change.
 */
suspend fun <T> awaitAsserted(timeout: Duration = 10.seconds, assertion: () -> T): T {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (true) {
        try {
            return assertion()
        } catch (failure: AssertionError) {
            if (deadline.hasPassedNow()) throw failure
        }
        delay(20.milliseconds)
    }
}
