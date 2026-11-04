package platform.network

import dev.isaacudy.udytils.urpc.client.urpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.websocket.WebSockets
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * The HTTP client and the urpc client factory every feature's generated service clients call
 * through. The application binds the [HttpClientEngine] and the [ServerEndpointConfig].
 */
val networkDependencies: Module = module {
    single {
        HttpClient(get<HttpClientEngine>()) {
            install(WebSockets)
        }
    } onClose { it?.close() }

    single {
        get<HttpClient>().urpcClient(baseUrl = get<ServerEndpointConfig>().baseUrl)
    } onClose { it?.close() }
}
