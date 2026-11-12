package platform.server.host

import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcServerInterceptor
import dev.isaacudy.udytils.urpc.server.ServiceErrorMapper
import io.ktor.server.websocket.WebSockets
import kotlin.time.Duration

/**
 * How [installServerHost] mounts urpc and configures WebSockets. Bind one in the server graph to
 * change them; without a binding the host mounts urpc at the root with urpc's defaults. The
 * Subcutaneous harness reads the same binding, so a test calls the services at the app's path.
 */
data class ServerHostConfig(
    val urpcRootPath: String,
    val errorMapper: ServiceErrorMapper,
    val logger: UrpcLogger,
    val idleTimeout: Duration?,
    val serverInterceptors: List<UrpcServerInterceptor>,
    val webSockets: WebSockets.WebSocketOptions.() -> Unit,
)
