package feature.sample.tests

import feature.sample.server.web.SamplePaths
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import platform.testing.robots.PageRobot
import platform.testing.robots.User

fun User.onMisnamedScreen(block: MisnamedPageRobot.() -> Unit) =
    on(SamplePaths.HOME, ::MisnamedPageRobot, block)

class MisnamedPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    fun seeHeading(text: String) = seeText(Locator.css("h1"), text)
}
