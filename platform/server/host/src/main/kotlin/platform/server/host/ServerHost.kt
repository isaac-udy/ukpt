package platform.server.host

import dev.isaacudy.udytils.postgres.PgNotificationBus
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.core.module.Module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.KoinApplicationStopPreparing
import org.koin.ktor.plugin.KoinIsolated
import platform.server.web.ContentSecurityPolicy
import platform.server.web.ServerPlugin
import platform.server.web.WebRoutes
import platform.server.web.installWebPlatform

/**
 * Serves every [WebRoutes] bound in [modules] from a Koin context owned by this [Application], so
 * several hosts can run in one process. Every bound [ServerPlugin] is installed first. The pages
 * carry the bound [ContentSecurityPolicy], or the platform's default when none is bound.
 */
fun Application.installServerHost(modules: List<Module>) {
    install(KoinIsolated) {
        modules(modules)
    }
    monitor.subscribe(KoinApplicationStopPreparing) { koinApplication ->
        koinApplication.koin.getOrNull<PgNotificationBus>()?.close()
    }
    val koin = getKoin()
    koin.getAll<ServerPlugin>().forEach { plugin -> with(plugin) { install() } }
    installWebPlatform(koin.getAll(), koin.getOrNull<ContentSecurityPolicy>() ?: ContentSecurityPolicy())
}
