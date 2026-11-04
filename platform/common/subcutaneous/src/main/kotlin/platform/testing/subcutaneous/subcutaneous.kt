package platform.testing.subcutaneous

import androidx.lifecycle.ViewModelStore
import dev.enro.result.NavigationResultChannel
import dev.enro.test.EnroTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import java.util.concurrent.Executors

/**
 * Runs [block] against [graph]: the server graph in-process on Ktor's test engine over a fresh
 * clone of the migrated database, and the client graph calling it. [edges] replace bindings at
 * the system's edges. Everything is torn down when [block] returns or throws.
 *
 * [block] runs on `Dispatchers.Main`, a dedicated thread, as ViewModel code does in the app.
 * Waits use real time: the server, the database and the transport do real I/O.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun subcutaneous(
    graph: SubcutaneousGraph,
    edges: Edges.() -> Unit = {},
    block: suspend SubcutaneousScope.() -> Unit,
) {
    val main = Executors.newSingleThreadScheduledExecutor { Thread(it, "subcutaneous-main").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    Dispatchers.setMain(main)
    try {
        runBlocking(main) { run(graph, Edges().apply(edges), block) }
    } finally {
        Dispatchers.resetMain()
        main.close()
    }
}

private suspend fun run(graph: SubcutaneousGraph, edges: Edges, block: suspend SubcutaneousScope.() -> Unit) =
    withRunningServer(graph.server, edges) { server, teardown ->
        val clientKoin = koinApplication { modules(graph.client + server.clientEdges) }.koin
        teardown { clientKoin.close() }

        EnroTest.installNavigationController()
        teardown {
            NavigationResultChannel.pendingResults.value = emptyMap()
            EnroTest.uninstallNavigationController()
        }

        val viewModels = ViewModelStore()
        teardown { viewModels.clear() }

        SubcutaneousScope(Scenario(server.koin) { clientKoin }, clientKoin, viewModels).block()
    }
