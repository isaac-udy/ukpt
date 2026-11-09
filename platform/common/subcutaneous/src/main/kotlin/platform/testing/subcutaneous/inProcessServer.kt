package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.PostgresConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.server.testing.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.mp.KoinPlatform
import platform.network.ServerEndpointConfig
import platform.server.host.installServerHost
import javax.sql.DataSource

/**
 * Runs [block] with [server]'s graph in-process on Ktor's test engine over a fresh clone of the
 * migrated database, and no client graph: the test starts the application's own, adding
 * [InProcessServerScope.clientEdges]. [edges] replace bindings at the system's edges.
 *
 * The client graph is expected in Koin's global context, where Compose's `KoinApplication` starts
 * it; teardown stops that context, then the server and the database.
 */
fun inProcessServer(
    server: (PostgresConfig) -> List<Module>,
    edges: Edges.() -> Unit = {},
    block: suspend InProcessServerScope.() -> Unit,
) = runBlocking {
    withRunningServer(server, Edges().apply(edges)) { running, teardown ->
        teardown { stopKoin() }
        val clientKoin = {
            KoinPlatform.getKoinOrNull() ?: error("client<T>() needs the client graph. Start the application's KoinApplication first.")
        }
        InProcessServerScope(Scenario(running.koin, clientKoin), running.clientEdges).block()
    }
}

internal class RunningServer(
    val koin: Koin,
    val clientEdges: List<Module>,
)

/** Starts [server] against a cloned database, runs [block], then runs every teardown step. */
internal suspend fun withRunningServer(
    server: (PostgresConfig) -> List<Module>,
    edges: Edges,
    block: suspend (RunningServer, Teardown) -> Unit,
) {
    val teardown = Teardown()
    try {
        val database = TestDatabases.clone()
        teardown { database.close() }

        lateinit var serverKoin: Koin
        val application = TestApplication {
            application {
                installServerHost(server(database.config) + edges.serverModule())
                serverKoin = getKoin()
            }
        }
        application.start()
        teardown {
            val dataSource = serverKoin.getOrNull<DataSource>()
            val exposed = serverKoin.getOrNull<Database>()
            application.stop()
            exposed?.let(TransactionManager::closeAndUnregister)
            (dataSource as? AutoCloseable)?.close()
        }

        val transport = module {
            single<HttpClientEngine> { application.client.engine }
            single { ServerEndpointConfig(baseUrl = "http://localhost") }
        }
        block(RunningServer(serverKoin, listOf(transport, edges.clientModule())), teardown)
    } catch (failure: Throwable) {
        teardown.runAll(failure)
        throw failure
    }
    teardown.runAll(null)
}

/** Runs the registered steps in reverse; a step's failure never skips the steps after it. */
internal class Teardown {
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
