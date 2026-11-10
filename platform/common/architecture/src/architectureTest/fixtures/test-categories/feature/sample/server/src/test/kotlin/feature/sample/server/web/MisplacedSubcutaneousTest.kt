package feature.sample.server.web

import feature.sample.tests.onSamplePage
import feature.sample.tests.sampleTestGraph
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test

@SubcutaneousTest
class MisplacedSubcutaneousTest {

    @Test
    fun `the page opens empty`() = subcutaneous(sampleTestGraph) {
        asUser(SamplePaths.HOME) {
            onSamplePage { seeSamples() }
        }
    }
}
