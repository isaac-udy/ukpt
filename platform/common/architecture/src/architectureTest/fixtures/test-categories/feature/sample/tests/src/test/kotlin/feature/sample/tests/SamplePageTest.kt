package feature.sample.tests

import feature.sample.server.web.SamplePaths
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test

@SubcutaneousTest
class SamplePageTest {

    @Test
    fun `the page lists the samples so far`() = subcutaneous(sampleTestGraph) {
        given { samples("a", "b") }

        asUser(SamplePaths.HOME) {
            onSamplePage { seeSamples("a", "b") }
        }
    }

    @Test
    fun `adding a label lists it`() = subcutaneous(sampleTestGraph) {
        asUser(SamplePaths.HOME) {
            onSamplePage {
                enterLabel("a")
                clickAddButton()
                seeSamples("a")
            }
        }
    }

    @Test
    fun `without scripts, an empty label re-renders the page with the error`() = subcutaneous(sampleTestGraph) {
        asUser(SamplePaths.HOME, scripts = false) {
            onSamplePage {
                clickAddButton()
                seeLabelError("Enter a label.")
            }
        }
    }
}
