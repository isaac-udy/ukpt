package feature.sample.server.web

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.flowOf
import platform.testing.ComponentTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ComponentTest
class RoutesComponentTest {

    @Test
    fun `the home page lists the samples`() = testApplication {
        val routes = SampleRoutes(flowOfSamples = { flowOf(listOf("a")) }, addSample = { })
        routing { with(routes) { install() } }

        assertEquals(true, "a" in client.get(SamplePaths.HOME).bodyAsText())
    }
}
