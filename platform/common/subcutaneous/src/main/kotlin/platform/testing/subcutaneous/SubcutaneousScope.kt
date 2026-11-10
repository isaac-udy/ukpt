package platform.testing.subcutaneous

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.testing.robots.User
import platform.testing.robots.asUser

/** The body of a [subcutaneous] test: seed state, then use the pages as a user does. */
class SubcutaneousScope internal constructor(
    private val scenario: Scenario,
    private val engine: HttpClientEngine,
) {
    /** Runs a scenario against the running server; before a user opens a page or while one is open. */
    suspend fun <T> given(block: suspend Scenario.() -> T): T = scenario.block()

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
