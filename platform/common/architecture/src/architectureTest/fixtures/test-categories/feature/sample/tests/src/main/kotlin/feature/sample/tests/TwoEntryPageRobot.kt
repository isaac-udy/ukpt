package feature.sample.tests

import feature.sample.server.web.SamplePaths
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import platform.testing.robots.PageRobot
import platform.testing.robots.User

fun User.onTwoEntryPage(block: TwoEntryPageRobot.() -> Unit) =
    on(SamplePaths.HOME, ::TwoEntryPageRobot, block)

fun User.onOtherPage(block: TwoEntryPageRobot.() -> Unit) =
    on(SamplePaths.ARCHIVE, ::TwoEntryPageRobot, block)

class TwoEntryPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    fun seeHeading(text: String) = seeText(Locator.css("h1"), text)
}
