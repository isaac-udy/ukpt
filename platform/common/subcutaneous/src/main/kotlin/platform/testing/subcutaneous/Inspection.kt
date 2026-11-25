package platform.testing.subcutaneous

import org.koin.core.Koin

/**
 * The receiver of a feature's server Inspections, which read the server's state for a test to
 * assert on: `suspend fun ServerInspection.storedGreetingTexts(): List<String>`. A test calls an
 * Inspection inside `inspectServer { }`.
 */
class ServerInspection internal constructor(@PublishedApi internal val serverKoin: Koin) {
    /** A binding from the running server's graph, such as a server domain read interface or a fake at a server edge. */
    inline fun <reified T : Any> server(): T = serverKoin.get()
}
