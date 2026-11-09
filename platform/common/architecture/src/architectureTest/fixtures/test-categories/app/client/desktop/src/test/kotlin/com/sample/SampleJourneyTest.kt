package com.sample

import androidx.compose.ui.test.v2.runComposeUiTest
import feature.sample.tests.onSampleScreen
import org.koin.compose.KoinApplication
import platform.testing.EndToEndTest
import platform.testing.robots.asUser
import platform.testing.subcutaneous.inProcessServer
import kotlin.test.Test

@EndToEndTest
class SampleJourneyTest {

    @Test
    fun `the seeded item shows`() = inProcessServer(::serverDependencies) {
        runComposeUiTest {
            setContent {
                KoinApplication(application = { modules(clientDependencies + clientEdges) }) {
                    AppContent()
                }
            }
            asUser(rootContext) {
                onSampleScreen { seeLabel("Seeded") }
            }
        }
    }
}
