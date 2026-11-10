package com.isaacudy.ukpt

import feature.ukpt.server.web.UkptPaths
import feature.ukpt.tests.greetingHistory
import feature.ukpt.tests.onGreetingsPage
import platform.testing.EndToEndTest
import platform.testing.browser.asUserInBrowser
import platform.testing.subcutaneous.inProcessServer
import kotlin.test.Test

@EndToEndTest
class GreetingJourneyTest {

    @Test
    fun `greeting someone and removing a greeting`() = inProcessServer(::serverDependencies) {
        given { greetingHistory("Hello, Grace") }

        asUserInBrowser(UkptPaths.HOME) {
            onGreetingsPage {
                seeGreetings("Hello, Grace")
                seeCharactersLeft("40 characters left")
                enterName("Ada")
                seeCharactersLeft("37 characters left")
                clickGreetButton()
                seeGreetings("Hello, Grace", "Hello, Ada")
                seeName("")
                clickRemoveButton("Hello, Grace")
                seeGreetings("Hello, Ada")
            }
        }
    }

    @Test
    fun `a greeting added elsewhere reaches an open page`() = inProcessServer(::serverDependencies) {
        asUserInBrowser(UkptPaths.HOME) {
            onGreetingsPage { seeNoGreetings() }

            given { greetingHistory("Kia ora") }

            onGreetingsPage { seeGreetings("Kia ora") }
        }
    }
}
