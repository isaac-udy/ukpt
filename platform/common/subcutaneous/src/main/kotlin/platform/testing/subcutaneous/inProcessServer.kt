package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.PostgresConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.ktor.ext.getKoin
import platform.server.host.installServerHost
import javax.sql.DataSource

/**
 * Runs [block] with [server]'s graph serving on a free local port, over a fresh clone of the
 * migrated database, for a real browser to load. [edges] replace bindings at the system's edges.
 */
fun inProcessServer(
    server: (PostgresConfig) -> List<Module>,
    edges: Edges.() -> Unit = {},
    block: suspend InProcessServerScope.() -> Unit,
) = runBlocking {
    val serverEdges = Edges().apply(edges)
    withTeardown { teardown ->
        val database = TestDatabases.clone()
        teardown { database.close() }

        lateinit var serverKoin: Koin
        val running = embeddedServer(Netty, port = 0, host = LOOPBACK) {
            installServerHost(server(database.config) + serverEdges.serverModule())
            serverKoin = getKoin()
        }.start(wait = false)
        teardown {
            val graphResources = ServerGraphResources(serverKoin)
            running.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
            graphResources.close()
        }

        val port = running.engine.resolvedConnectors().first().port
        InProcessServerScope(Scenario(serverKoin), baseUrl = "http://$LOOPBACK:$port").block()
    }
}

private const val LOOPBACK = "127.0.0.1"

/** What the server graph opened that stopping the application leaves open. Read before the stop closes Koin. */
internal class ServerGraphResources(koin: Koin) {
    private val dataSource = koin.getOrNull<DataSource>()
    private val exposed = koin.getOrNull<Database>()

    fun close() {
        exposed?.let(TransactionManager::closeAndUnregister)
        (dataSource as? AutoCloseable)?.close()
    }
}

/** Runs [block], then every registered teardown step in reverse; a step's failure never skips the steps after it. */
internal suspend fun withTeardown(block: suspend (Teardown) -> Unit) {
    val teardown = Teardown()
    try {
        block(teardown)
    } catch (failure: Throwable) {
        teardown.runAll(failure)
        throw failure
    }
    teardown.runAll(null)
}

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
