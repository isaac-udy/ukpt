package com.isaacudy.ukpt

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.enro.ui.LocalRootContext
import feature.ukpt.tests.greetingHistory
import feature.ukpt.tests.onConfirmResetDialog
import feature.ukpt.tests.onUkptScreen
import org.koin.compose.KoinApplication
import platform.testing.EndToEndTest
import platform.testing.robots.asUser
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

            asUser(rootContext) {
                onUkptScreen {
                    seeGreeting("Hi there")
                    clickGreetButton()
                    seeGreeting("Hello again")
                    seeGreetingCount(2)
                    clickResetButton()
                }
                onConfirmResetDialog {
                    clickResetButton()
                }
                onUkptScreen {
                    seeNoGreetings()
                }
            }
        }
    }
}
