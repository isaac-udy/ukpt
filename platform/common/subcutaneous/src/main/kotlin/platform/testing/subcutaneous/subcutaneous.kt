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
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import java.util.concurrent.Executors

/**
 * Runs [block] against [graph]: the client graph, calling the graph's server when it has one,
 * in-process over a fresh clone of the migrated database. [edges] replace bindings at the system's
 * edges. Everything is torn down when [block] returns or throws.
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
        runBlocking(main) { run(graph, Edges().apply(graph.hostEdges), Edges().apply(edges), block) }
    } finally {
        Dispatchers.resetMain()
        main.close()
    }
}

private suspend fun run(graph: SubcutaneousGraph, hostEdges: Edges, edges: Edges, block: suspend SubcutaneousScope.() -> Unit) {
    val server = graph.server
    if (server == null) {
        require(!hostEdges.hasServerBindings && !edges.hasServerBindings) { "server { … } edges need a test graph with a server. Bind a backend the project does not own with client { … }." }
        withClient(graph.client + hostEdges.clientModule() + edges.clientModule(), running = null, block)
    } else {
        server.run(listOf(hostEdges.serverModule(), edges.serverModule())) { running ->
            withClient(graph.client + running.clientModules + hostEdges.clientModule() + edges.clientModule(), running, block)
        }
    }
}

private suspend fun withClient(
    modules: List<Module>,
    running: RunningServer?,
    block: suspend SubcutaneousScope.() -> Unit,
) = withTeardown { teardown ->
    val clientKoin = koinApplication { modules(modules) }.koin
    teardown { clientKoin.close() }

    EnroTest.installNavigationController()
    teardown {
        NavigationResultChannel.pendingResults.value = emptyMap()
        EnroTest.uninstallNavigationController()
    }

    val viewModels = ViewModelStore()
    teardown { viewModels.clear() }

    SubcutaneousScope(Scenario(running?.koin) { clientKoin }, clientKoin, viewModels, running?.services).block()
}
