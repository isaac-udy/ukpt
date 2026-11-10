package feature.shop.server.web

import feature.shop.server.domain.FindSession
import feature.shop.server.data.SessionRepository
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import platform.server.web.ServerPlugin

internal class SessionPlugin(private val findSession: FindSession) : ServerPlugin {
    override fun Application.install() {
        install(Authentication) { }
    }
}

class PublicPlugin : ServerPlugin {
    override fun Application.install() = Unit
}

internal class DetachedPlugin {
    fun Application.install() = Unit
}

internal class StoragePlugin(private val sessions: SessionRepository) : ServerPlugin {
    override fun Application.install() = Unit
}
