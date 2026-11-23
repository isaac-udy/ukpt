package platform.testing.subcutaneous

import androidx.lifecycle.ViewModelStore
import dev.enro.result.NavigationResultChannel
import dev.enro.test.EnroTest
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [block] against [graph]: the client graph, calling the graph's server when it has one,
 * in-process over a fresh clone of the migrated database. [edges] replace bindings at the system's
 * edges. Everything is torn down when [block] returns or throws.
 *
 * [block] runs on `Dispatchers.Main`, a dedicated thread (the event loop on JS and wasm), as
 * ViewModel code does in the app. Waits use real time: the server, the database and the transport
 * do real I/O. A test returns the result as its expression body: on JS and wasm the result is the
 * Promise the test runner waits for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun subcutaneous(
    graph: SubcutaneousGraph,
    edges: Edges.() -> Unit = {},
    block: suspend SubcutaneousScope.() -> Unit,
): TestResult = runTest(timeout = 60.seconds) {
    val main = mainDispatcher()
    Dispatchers.setMain(main)
    try {
        withContext(main) { run(graph, Edges().apply(graph.hostEdges), Edges().apply(edges), block) }
    } finally {
        Dispatchers.resetMain()
        (main as? CloseableCoroutineDispatcher)?.close()
    }
}

/** The dispatcher [subcutaneous] installs as `Dispatchers.Main`. */
internal expect fun mainDispatcher(): CoroutineDispatcher

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
