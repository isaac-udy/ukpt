package platform.testing.subcutaneous

import org.koin.core.Koin
import org.koin.core.module.Module
import kotlin.reflect.KClass

/**
 * The production Koin modules a feature's Subcutaneous Tests run: [client] builds the ViewModels,
 * and [server], when the project has one, runs in-process for the client to call. A project whose
 * backend it does not own has no [server]; its tests fake that backend at the client edge.
 */
class SubcutaneousGraph(
    val client: List<Module>,
    val server: SubcutaneousServer? = null,
)

/**
 * A server a Subcutaneous Test starts for its client graph to call. `featureServer(…)` in
 * `:platform:common:subcutaneous-server` is the in-process Ktor server over embedded Postgres.
 */
interface SubcutaneousServer {
    /** Starts the server with [edges] loaded after its graph, runs [block], then stops it. */
    suspend fun run(edges: Module, block: suspend (RunningServer) -> Unit)
}

/** A started [SubcutaneousServer]. */
class RunningServer(
    /** The server's graph, which a scenario's `server<T>()` resolves from. */
    val koin: Koin,
    /** Loaded after the client graph: the transport that reaches this server. */
    val clientModules: List<Module>,
    /** Builds a client for the server-side service [KClass] from the client graph. */
    val services: (clientKoin: Koin, service: KClass<*>) -> Any,
)
