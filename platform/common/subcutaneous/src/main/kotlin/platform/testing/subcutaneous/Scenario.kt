package platform.testing.subcutaneous

import org.koin.core.Koin

/**
 * The receiver of a feature's scenario functions, which put the database into a state through
 * the server domain: `suspend fun Scenario.greetingHistory(vararg texts: String)`.
 */
class Scenario internal constructor(
    @PublishedApi internal val serverKoin: Koin,
) {
    /** A binding from the running server's graph, such as a server domain interface. */
    inline fun <reified T : Any> server(): T = serverKoin.get()
}
