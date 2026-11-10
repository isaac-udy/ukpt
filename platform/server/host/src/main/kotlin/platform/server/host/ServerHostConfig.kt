package platform.server.host

import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcServerInterceptor
import dev.isaacudy.udytils.urpc.server.ServiceErrorMapper
import io.ktor.server.websocket.WebSockets
import kotlin.time.Duration

/**
 * How [installServerHost] mounts urpc and configures WebSockets. Bind one in the server graph to
 * change them; without a binding the host uses these defaults. The Subcutaneous harness reads the
 * same binding, so a test reaches the services where the app does.
 */
class ServerHostConfig(
    val urpcRootPath: String = "",
    val errorMapper: ServiceErrorMapper = ServiceErrorMapper.Default,
    val logger: UrpcLogger = UrpcLogger.NoOp,
    val idleTimeout: Duration? = null,
    val serverInterceptors: List<UrpcServerInterceptor> = emptyList(),
    val webSockets: WebSockets.WebSocketOptions.() -> Unit = {},
)
