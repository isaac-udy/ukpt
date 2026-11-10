package feature.sample.tests

import feature.sample.server.web.SamplePaths
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import platform.testing.robots.PageRobot
import platform.testing.robots.User

fun User.onMisplacedPage(block: MisplacedPageRobot.() -> Unit) =
    on(SamplePaths.HOME, ::MisplacedPageRobot, block)

class MisplacedPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    fun seeHeading(text: String) = seeText(Locator.css("h1"), text)
}
