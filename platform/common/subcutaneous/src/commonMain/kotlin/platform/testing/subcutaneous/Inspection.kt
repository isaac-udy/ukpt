package platform.testing.subcutaneous

import org.koin.core.Koin

/**
 * The receiver of a feature's server Inspections, which read the server's state for a test to
 * assert on: `suspend fun ServerInspection.storedGreetings(): List<String>`. A test calls an
 * Inspection inside `inspectServer { }`.
 */
class ServerInspection internal constructor(@PublishedApi internal val serverKoin: Koin) {
    /** A binding from the running server's graph, such as a server domain read interface or a fake at a server edge. */
    inline fun <reified T : Any> server(): T = serverKoin.get()
}

/**
 * The receiver of a feature's client Inspections, which read the client's state for a test to
 * assert on, such as the calls a fake at a client edge received. A test calls an Inspection inside
 * `inspectClient { }`.
 */
class ClientInspection internal constructor(@PublishedApi internal val clientKoin: Koin) {
    /** A binding from the client graph, such as a client domain read interface or a fake at a client edge. */
    inline fun <reified T : Any> client(): T = clientKoin.get()
}
