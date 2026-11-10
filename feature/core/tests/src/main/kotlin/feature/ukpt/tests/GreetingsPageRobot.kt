package feature.ukpt.tests

import feature.ukpt.server.web.UkptIds
import feature.ukpt.server.web.UkptPaths
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import platform.testing.robots.PageRobot
import platform.testing.robots.User

fun User.onGreetingsPage(block: GreetingsPageRobot.() -> Unit) =
    on(setOf(UkptPaths.HOME, UkptPaths.GREETINGS), ::GreetingsPageRobot, block)

class GreetingsPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    private val form = Locator.css(UkptIds.greetingForm.selector)
    private val greetings = Locator.css("${UkptIds.greetings.selector} > li")

    fun enterName(name: String) = enter(form.find("input[name=name]"), name)

    fun clickGreetButton() = click(form.find("button[type=submit]", hasText = "Greet"))

    fun clickRemoveButton(greeting: String) =
        click(Locator.css("${UkptIds.greetings.selector} > li", hasText = greeting).find("button", hasText = "Remove"))

    fun seeGreetings(vararg texts: String) = seeTexts(greetings.find("span"), texts.toList())

    fun seeNoGreetings() = seeNone(greetings)

    fun seeNameError(text: String) = seeText(form.find(".field-error"), text)

    fun seeName(name: String) = seeValue(form.find("input[name=name]"), name)

    /** Alpine renders this count; only a browser shows it. */
    fun seeCharactersLeft(text: String) = seeText(form.find("p.muted span"), text)
}
