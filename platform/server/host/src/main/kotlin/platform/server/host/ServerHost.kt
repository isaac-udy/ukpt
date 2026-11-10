package platform.server.host

import dev.isaacudy.udytils.postgres.PgNotificationBus
import dev.isaacudy.udytils.urpc.koin.urpcWithKoin
import dev.isaacudy.udytils.urpc.server.ServiceErrorMapper
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
 * [Application], so several hosts and a client graph can run in one process. A
 * [ServiceErrorMapper] bound in [modules] maps service exceptions to statuses.
 */
fun Application.installServerHost(modules: List<Module>) {
    install(KoinIsolated) {
        modules(modules)
    }
    install(WebSockets)
    monitor.subscribe(KoinApplicationStopPreparing) { koinApplication ->
        koinApplication.koin.getOrNull<PgNotificationBus>()?.close()
    }
    val errorMapper = getKoin().getOrNull<ServiceErrorMapper>() ?: ServiceErrorMapper.Default
    routing {
        get("/") {
            call.respondText("Hello, ukpt server!")
        }
        urpcWithKoin(errorMapper = errorMapper)
    }
}
