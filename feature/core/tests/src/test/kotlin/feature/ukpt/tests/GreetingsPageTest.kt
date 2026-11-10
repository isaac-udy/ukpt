package feature.ukpt.tests

import feature.ukpt.server.web.UkptPaths
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test

@SubcutaneousTest
class GreetingsPageTest {

    @Test
    fun `the page lists the greetings so far`() = subcutaneous(ukptTestGraph) {
        given { greetingHistory("Hello, Ada", "Hello, Grace") }

        asUser(UkptPaths.HOME) {
            onGreetingsPage { seeGreetings("Hello, Ada", "Hello, Grace") }
        }
    }

    @Test
    fun `greeting a name adds it to the list and clears the form`() = subcutaneous(ukptTestGraph) {
        asUser(UkptPaths.HOME) {
            onGreetingsPage {
                seeNoGreetings()
                enterName("Ada")
                clickGreetButton()
                seeGreetings("Hello, Ada")
                seeName("")
            }
        }
    }

    @Test
    fun `a name greeted before is greeted again`() = subcutaneous(ukptTestGraph) {
        given { greetingHistory("Hello, Ada") }

        asUser(UkptPaths.HOME) {
            onGreetingsPage {
                enterName("Ada")
                clickGreetButton()
                seeGreetings("Hello, Ada", "Hello again, Ada")
            }
        }
    }

    @Test
    fun `an empty name shows an error and greets no one`() = subcutaneous(ukptTestGraph) {
        asUser(UkptPaths.HOME) {
            onGreetingsPage {
                enterName("  ")
                clickGreetButton()
                seeNameError("Enter a name.")
                seeNoGreetings()
            }
        }
    }

    @Test
    fun `removing a greeting takes it off the list`() = subcutaneous(ukptTestGraph) {
        given { greetingHistory("Hello, Ada", "Hello, Grace") }

        asUser(UkptPaths.HOME) {
            onGreetingsPage {
                clickRemoveButton("Hello, Ada")
                seeGreetings("Hello, Grace")
            }
        }
    }

    @Test
    fun `a greeting added elsewhere appears on an open page`() = subcutaneous(ukptTestGraph) {
        asUser(UkptPaths.HOME) {
            onGreetingsPage { seeNoGreetings() }

            given { greetingHistory("Kia ora") }

            onGreetingsPage { seeGreetings("Kia ora") }
        }
    }

    @Test
    fun `without scripts, greeting a name redirects back to the list`() = subcutaneous(ukptTestGraph) {
        asUser(UkptPaths.HOME, scripts = false) {
            onGreetingsPage {
                enterName("Ada")
                clickGreetButton()
            }
            onGreetingsPage { seeGreetings("Hello, Ada") }
        }
    }

    @Test
    fun `without scripts, an empty name re-renders the page with the error`() = subcutaneous(ukptTestGraph) {
        asUser(UkptPaths.HOME, scripts = false) {
            onGreetingsPage {
                clickGreetButton()
                seeNameError("Enter a name.")
            }
        }
    }

    @Test
    fun `without scripts, removing a greeting redirects back to the list`() = subcutaneous(ukptTestGraph) {
        given { greetingHistory("Hello, Ada") }

        asUser(UkptPaths.HOME, scripts = false) {
            onGreetingsPage { clickRemoveButton("Hello, Ada") }
            onGreetingsPage { seeNoGreetings() }
        }
    }
}
