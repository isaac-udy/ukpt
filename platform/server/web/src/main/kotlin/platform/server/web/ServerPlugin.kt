package platform.server.web

import io.ktor.server.application.Application

/**
 * Ktor plugins the routes rely on, such as authentication or call logging. Each implementation is
 * bound in a dependency module with `bind ServerPlugin::class`, and the server installs every
 * binding before the web platform and the routes. Koin returns the bindings in no defined order,
 * so plugins whose order matters are installed by one binding.
 */
fun interface ServerPlugin {
    fun Application.install()
}
