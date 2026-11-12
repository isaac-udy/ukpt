package platform.testing.subcutaneous

import dev.enro.NavigationKey
import dev.enro.test.TestNavigationHandle
import dev.enro.test.assertClosed
import dev.enro.test.assertOpened
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Suspends until this handle has opened [key] and returns its instance; fails after [timeout] of
 * real time. For an open the ViewModel performs later, such as when the ViewModel
 * receives a result the ViewModel registered for.
 */
suspend inline fun <reified K : NavigationKey> TestNavigationHandle<*>.awaitOpened(
    key: K,
    timeout: Duration = 10.seconds,
): NavigationKey.Instance<K> = awaitAsserted(timeout) { assertOpened(key) }

/**
 * Suspends until this handle has opened a [K] matching [predicate] and returns its instance; fails
 * after [timeout] of real time. For a key the test can't build, such as a confirmation dialog.
 */
suspend inline fun <reified K : NavigationKey> TestNavigationHandle<*>.awaitOpened(
    timeout: Duration = 10.seconds,
    crossinline predicate: (K) -> Boolean = { true },
): NavigationKey.Instance<K> = awaitAsserted(timeout) {
    // assertOpened<K> alone matches any open: the operation's type argument is erased.
    assertOpened<K> { instance -> instance.key.let { it is K && predicate(it) } }
}

/** Suspends until this handle has closed; fails after [timeout] of real time. */
suspend fun TestNavigationHandle<*>.awaitClosed(timeout: Duration = 10.seconds) {
    awaitAsserted(timeout) { assertClosed() }
}
