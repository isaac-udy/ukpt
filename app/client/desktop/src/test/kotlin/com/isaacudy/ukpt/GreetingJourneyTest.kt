package com.isaacudy.ukpt

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.enro.ui.LocalRootContext
import feature.ukpt.tests.greetingHistory
import org.koin.compose.KoinApplication
import platform.testing.EndToEndTest
import platform.testing.subcutaneous.inProcessServer
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
@EndToEndTest
class GreetingJourneyTest {

    @Test
    fun `a seeded greeting shows, Greet adds one, and a confirmed reset empties the history`() = inProcessServer(::serverDependencies) {
        given { greetingHistory("Hi there") }
        val rootContext = appRootContext()

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalRootContext provides rootContext) {
                    KoinApplication(application = { modules(clientDependencies + clientEdges) }) {
                        AppContent()
                    }
                }
            }
            awaitText("Hi there")

            onNodeWithText("Greet").performClick()
            awaitText("Hello again")

            onNodeWithText("Reset").performClick()
            awaitText("Reset greeting count?")
            onNode(hasText("Reset") and hasAnyAncestor(isDialog())).performClick()

            awaitText("No greetings yet")
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.awaitText(text: String) = waitUntilExactlyOneExists(hasText(text), timeoutMillis = 10_000)
