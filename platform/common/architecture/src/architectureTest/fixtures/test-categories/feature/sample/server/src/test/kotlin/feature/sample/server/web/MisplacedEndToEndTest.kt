package feature.sample.server.web

import feature.sample.sampleServerDependencies
import feature.sample.tests.onSamplePage
import platform.testing.EndToEndTest
import platform.testing.browser.asUserInBrowser
import platform.testing.subcutaneous.inProcessServer
import kotlin.test.Test

@EndToEndTest
class MisplacedEndToEndTest {

    @Test
    fun `the page opens empty`() = inProcessServer({ listOf(sampleServerDependencies) }) {
        asUserInBrowser(SamplePaths.HOME) {
            onSamplePage { seeSamples() }
        }
    }
}
