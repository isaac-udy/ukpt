package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.urpc.UrpcClientFactory
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
import platform.server.host.ServerHostConfig
import platform.server.host.installServerHost
import javax.sql.DataSource
import kotlin.reflect.KClass

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
    val applied = Edges().apply(edges)
    InProcessServer(server).run(applied.serverModule()) { running ->
        withTeardown { teardown ->
            teardown { stopKoin() }
            val clientKoin = {
                KoinPlatform.getKoinOrNull() ?: error("client<T>() needs the client graph. Start the application's KoinApplication first.")
            }
            InProcessServerScope(Scenario(running.koin, clientKoin), running.clientModules + applied.clientModule()).block()
        }
    }
}

/** [modules] installed in Ktor's test engine against a fresh clone of the migrated database. */
internal class InProcessServer(
    private val modules: (PostgresConfig) -> List<Module>,
) : SubcutaneousServer {

    override suspend fun run(edges: Module, block: suspend (RunningServer) -> Unit) = withTeardown { teardown ->
        val database = TestDatabases.clone()
        teardown { database.close() }

        lateinit var serverKoin: Koin
        val application = TestApplication {
            application {
                installServerHost(modules(database.config) + edges)
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

        val urpcRootPath = serverKoin.getOrNull<ServerHostConfig>()?.urpcRootPath.orEmpty()
        val transport = module {
            single<HttpClientEngine> { application.client.engine }
            single { ServerEndpointConfig(baseUrl = "http://localhost$urpcRootPath") }
        }
        block(RunningServer(serverKoin, listOf(transport), ::urpcClient))
    }
}

/** The generated urpc client of [service], built from the client graph's [UrpcClientFactory]. */
private fun urpcClient(clientKoin: Koin, service: KClass<*>): Any {
    val clientClass = runCatching { Class.forName("${service.java.name}UrpcClient") }.getOrElse {
        throw IllegalArgumentException("${service.qualifiedName} has no generated urpc client. Annotate the interface with @Urpc.", it)
    }
    val constructor = clientClass.getDeclaredConstructor(UrpcClientFactory::class.java).apply { isAccessible = true }
    return constructor.newInstance(clientKoin.get<UrpcClientFactory>())
}
