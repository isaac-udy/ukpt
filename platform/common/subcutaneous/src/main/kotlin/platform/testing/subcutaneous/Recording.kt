package platform.testing.subcutaneous

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The calls a Fake received, in order. The Fake calls [record] from each faked function; a test
 * reads the calls inside `inspectServer { }`, which retries until the assertion passes.
 */
class Recording<T> {
    private val recorded = MutableStateFlow(emptyList<T>())

    val calls: List<T> get() = recorded.value

    fun record(call: T) {
        recorded.update { it + call }
    }

    /** Returns the one call matching [predicate]; fails listing every call when no call or several calls match. */
    fun assertSingle(predicate: (T) -> Boolean = { true }): T {
        val all = calls
        val matching = all.filter(predicate)
        if (matching.size != 1) throw AssertionError("Expected one matching call, found ${matching.size}. Calls: $all")
        return matching.single()
    }

    /** Fails listing every call when a call matches [predicate]. */
    fun assertNone(predicate: (T) -> Boolean = { true }) {
        val all = calls
        if (all.any(predicate)) throw AssertionError("Expected no matching call. Calls: $all")
    }
}
