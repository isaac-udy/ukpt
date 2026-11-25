package platform.testing.subcutaneous

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.testing.robots.User
import platform.testing.robots.asUser
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The body of a [subcutaneous] test: seed state, use the pages as a user does, then inspect the server. */
class SubcutaneousScope internal constructor(
    private val scenario: Scenario,
    private val engine: HttpClientEngine,
) {
    /** Runs a scenario against the running server; before a user opens a page or while one is open. */
    suspend fun <T> given(block: suspend Scenario.() -> T): T = scenario.block()

    /**
     * Runs [block] against the running server until [block] passes and returns its result; rethrows
     * its last failure after [timeout] of real time.
     */
    suspend fun <T> inspectServer(timeout: Duration = 10.seconds, block: suspend ServerInspection.() -> T): T {
        val inspection = ServerInspection(scenario.serverKoin)
        return awaitAsserted(timeout) { inspection.block() }
    }

    /**
     * Opens [start] in a new tab with its own cookies, then runs [block] as the user of that tab.
     * The tab runs htmx's requests, swaps and event streams; with [scripts] false it behaves as a
     * browser with JavaScript turned off. Alpine and the project's own scripts never run here.
     */
    suspend fun asUser(start: String, scripts: Boolean = true, block: suspend User.() -> Unit) {
        val tab = HtmxTab(engine, scripts)
        try {
            withContext(Dispatchers.IO) { asUser(tab, start, block) }
        } finally {
            tab.close()
        }
    }
}
