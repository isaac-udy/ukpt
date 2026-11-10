package feature.sample.tests

import feature.sample.server.web.SamplePaths
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import platform.testing.robots.PageRobot
import platform.testing.robots.User

fun User.onSummaryPage(block: SummaryPageRobot.() -> Unit) =
    on(SamplePaths.HOME, ::SummaryPageRobot, block)

class SummaryPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    fun seeSummary(text: String) = seeText(Locator.css("p.summary"), text)
}
