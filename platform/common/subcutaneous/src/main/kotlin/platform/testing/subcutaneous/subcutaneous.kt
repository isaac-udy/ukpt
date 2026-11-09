package platform.testing.subcutaneous

import androidx.lifecycle.ViewModelStore
import dev.enro.result.NavigationResultChannel
import dev.enro.test.EnroTest
import io.ktor.client.engine.HttpClientEngine
import io.ktor.server.testing.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import platform.network.ServerEndpointConfig
import platform.server.host.installServerHost
import java.util.concurrent.Executors
import javax.sql.DataSource

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

private suspend fun run(graph: SubcutaneousGraph, edges: Edges, block: suspend SubcutaneousScope.() -> Unit) {
    val teardown = Teardown()
    try {
        val database = TestDatabases.clone()
        teardown { database.close() }

        lateinit var serverKoin: Koin
        val server = TestApplication {
            application {
                installServerHost(graph.server(database.config) + edges.serverModule())
                serverKoin = getKoin()
            }
        }
        server.start()
        teardown {
            val dataSource = serverKoin.getOrNull<DataSource>()
            val exposed = serverKoin.getOrNull<Database>()
            server.stop()
            exposed?.let(TransactionManager::closeAndUnregister)
            (dataSource as? AutoCloseable)?.close()
        }

        val transport = module {
            single<HttpClientEngine> { server.client.engine }
            single { ServerEndpointConfig(baseUrl = "http://localhost") }
        }
        val clientKoin = koinApplication { modules(graph.client + transport + edges.clientModule()) }.koin
        teardown { clientKoin.close() }

        EnroTest.installNavigationController()
        teardown {
            NavigationResultChannel.pendingResults.value = emptyMap()
            EnroTest.uninstallNavigationController()
        }

        val viewModels = ViewModelStore()
        teardown { viewModels.clear() }

        SubcutaneousScope(Scenario(serverKoin, clientKoin), clientKoin, viewModels).block()
    } catch (failure: Throwable) {
        teardown.runAll(failure)
        throw failure
    }
    teardown.runAll(null)
}

/** Runs the registered steps in reverse; a step's failure never skips the steps after it. */
private class Teardown {
    private val steps = ArrayDeque<suspend () -> Unit>()

    operator fun invoke(step: suspend () -> Unit) {
        steps.addFirst(step)
    }

    suspend fun runAll(primary: Throwable?) {
        var failure = primary
        while (steps.isNotEmpty()) {
            try {
                steps.removeFirst().invoke()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        if (primary == null && failure != null) throw failure
    }
}
