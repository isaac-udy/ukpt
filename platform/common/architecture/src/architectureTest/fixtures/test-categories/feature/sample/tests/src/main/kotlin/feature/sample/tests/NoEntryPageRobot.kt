package feature.sample.tests

import feature.sample.server.web.SampleIds
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import platform.testing.robots.PageRobot

// Names fun User.onNoEntryPage( only in this comment.
class NoEntryPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    fun save() = click(Locator.css(SampleIds.sampleForm.selector).find("button[type=submit]"))

    fun seeSaved(text: String) = seeText(Locator.css("p.saved"), text)
}
