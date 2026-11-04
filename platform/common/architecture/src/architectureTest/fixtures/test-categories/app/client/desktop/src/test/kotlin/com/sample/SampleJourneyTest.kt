package com.sample

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import org.koin.compose.KoinApplication
import platform.testing.EndToEndTest
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
            awaitText("Seeded")
        }
    }
}

private fun ComposeUiTest.awaitText(text: String) = waitUntilExactlyOneExists(hasText(text))
