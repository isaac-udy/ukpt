package platform.testing.subcutaneous

import io.ktor.server.testing.TestApplication
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.ktor.ext.getKoin
import platform.server.host.installServerHost

/**
 * Runs [block] against [graph]: the server graph in-process on Ktor's test engine over a fresh
 * clone of the migrated database. [edges] replace bindings at the system's edges. Everything is
 * torn down when [block] returns or throws.
 *
 * Waits use real time: the server, the database and the event streams do real I/O.
 */
fun subcutaneous(
    graph: SubcutaneousGraph,
    edges: Edges.() -> Unit = {},
    block: suspend SubcutaneousScope.() -> Unit,
) = runBlocking {
    val serverEdges = Edges().apply(edges)
    withTeardown { teardown ->
        val database = TestDatabases.clone()
        teardown { database.close() }

        lateinit var serverKoin: Koin
        val application = TestApplication {
            application {
                installServerHost(graph.server(database.config) + serverEdges.serverModule())
                serverKoin = getKoin()
            }
        }
        application.start()
        teardown {
            val graphResources = ServerGraphResources(serverKoin)
            application.stop()
            graphResources.close()
        }

        SubcutaneousScope(Scenario(serverKoin), application.client.engine).block()
    }
}
