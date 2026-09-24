package platform.server.web

import io.ktor.server.routing.Route

/**
 * The HTTP routes of one feature's `server.web` layer. Each implementation is bound in its
 * feature's dependency module with `bind WebRoutes::class`, and the server installs every binding.
 */
interface WebRoutes {
    fun Route.install()
}
