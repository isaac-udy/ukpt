package platform.server.host

import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.plugin
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.server.web.ContentSecurityPolicy
import platform.server.web.ServerPlugin
import platform.server.web.WebRoutes
import platform.testing.AdapterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@AdapterTest
class ServerHostTest {

    private val accountRoutes = object : WebRoutes {
        override fun Route.install() {
            authenticate("accounts") {
                get("/account") { call.respondText("Signed in as ${call.principal<UserIdPrincipal>()?.name}") }
            }
        }
    }

    private val accountsPlugin = ServerPlugin {
        install(Authentication) {
            basic("accounts") {
                validate { credentials -> if (credentials.password == "secret") UserIdPrincipal(credentials.name) else null }
            }
        }
    }

    @Test
    fun `a bound server plugin is installed before the routes that rely on it`() = testApplication {
        application {
            installServerHost(listOf(module {
                single { accountsPlugin } bind ServerPlugin::class
                single { accountRoutes } bind WebRoutes::class
            }))
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/account").status)
        assertEquals("Signed in as ada", client.get("/account") { basicAuth("ada", "secret") }.bodyAsText())
    }

    @Test
    fun `websockets keep the configuration of a plugin that installed them`() = testApplication {
        lateinit var host: Application
        application {
            installServerHost(listOf(module {
                single { ServerPlugin { install(WebSockets) { timeoutMillis = 3_000 } } } bind ServerPlugin::class
            }))
            host = this
        }
        startApplication()

        assertEquals(3_000, host.plugin(WebSockets).timeoutMillis)
    }

    @Test
    fun `pages carry the bound content security policy, or the platform's default`() {
        assertPolicy(module { single { ContentSecurityPolicy(imageSources = listOf("https://images.example")) } }, "img-src 'self' data: https://images.example;")
        assertPolicy(module { }, "img-src 'self' data:;")
    }

    private fun assertPolicy(bindings: Module, imageSources: String) = testApplication {
        application { installServerHost(listOf(bindings)) }
        val policy = client.get("/missing").headers["Content-Security-Policy"].orEmpty()
        assertTrue(imageSources in policy, policy)
    }
}
