package com.sample

import feature.sample.server.web.SamplePaths
import feature.sample.tests.onSamplePage
import feature.sample.tests.onSummaryPage
import feature.sample.tests.samples
import platform.testing.EndToEndTest
import platform.testing.browser.asUserInBrowser
import platform.testing.subcutaneous.inProcessServer
import kotlin.test.Test

@EndToEndTest
class SampleJourneyTest {

    @Test
    fun `adding a label`() = inProcessServer(::serverDependencies) {
        given { samples("a") }

        asUserInBrowser(SamplePaths.HOME) {
            onSamplePage {
                seeSamples("a")
                enterLabel("b")
                clickAddButton()
                seeSamples("a", "b")
            }
            onSummaryPage { seeSummary("2 samples") }
        }
    }
}
