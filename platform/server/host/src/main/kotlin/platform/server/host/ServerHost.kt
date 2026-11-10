package platform.server.host

import dev.isaacudy.udytils.postgres.PgNotificationBus
import dev.isaacudy.udytils.urpc.koin.urpcWithKoin
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import org.koin.core.module.Module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.KoinApplicationStopPreparing
import org.koin.ktor.plugin.KoinIsolated

/**
 * Serves every `@Urpc` service bound in [modules] from a Koin context owned by this
 * [Application], so several hosts and a client graph can run in one process. A [ServerHostConfig]
 * bound in [modules] sets where urpc is mounted and how.
 */
fun Application.installServerHost(modules: List<Module>) {
    install(KoinIsolated) {
        modules(modules)
    }
    val config = getKoin().getOrNull<ServerHostConfig>() ?: ServerHostConfig()
    install(WebSockets, config.webSockets)
    monitor.subscribe(KoinApplicationStopPreparing) { koinApplication ->
        koinApplication.koin.getOrNull<PgNotificationBus>()?.close()
    }
    routing {
        get("/") {
            call.respondText("Hello, ukpt server!")
        }
        urpcWithKoin(
            rootPath = config.urpcRootPath,
            errorMapper = config.errorMapper,
            logger = config.logger,
            idleTimeout = config.idleTimeout,
            serverInterceptors = config.serverInterceptors,
        )
    }
}
