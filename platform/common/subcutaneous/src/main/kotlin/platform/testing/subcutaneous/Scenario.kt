package platform.testing.subcutaneous

import org.koin.core.Koin

/**
 * The receiver of a feature's scenario functions, which put the system into a state: the database
 * through the server domain (`suspend fun Scenario.greetingHistory(vararg texts: String)`), or,
 * without a server, the fake backend bound at the client edge.
 */
class Scenario(
    @PublishedApi internal val serverKoin: Koin?,
    @PublishedApi internal val clientKoin: () -> Koin,
) {
    /** A binding from the running server's graph, such as a server domain interface. */
    inline fun <reified T : Any> server(): T =
        (serverKoin ?: error("server<${T::class.simpleName}>() needs a test graph with a server. Seed a backend the project does not own through client<T>().")).get()

    /** A binding from the client graph, such as a fake bound with `edges = { client { … } }`. */
    inline fun <reified T : Any> client(): T = clientKoin().get()
}
