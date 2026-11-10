package feature.sample.tests

import feature.sample.server.web.SampleIds
import feature.sample.server.web.SamplePaths
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import platform.testing.robots.PageRobot
import platform.testing.robots.User

fun User.onSamplePage(block: SamplePageRobot.() -> Unit) =
    on(setOf(SamplePaths.HOME, SamplePaths.SAMPLES), ::SamplePageRobot, block)

class SamplePageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    private val form = Locator.css(SampleIds.sampleForm.selector)
    private val samples = Locator.css("${SampleIds.samples.selector} > li")

    fun enterLabel(label: String) = enter(form.find("input[name=label]"), label)

    fun clickAddButton() = click(form.find("button[type=submit]", hasText = "Add"))

    fun seeSamples(vararg labels: String) = seeTexts(samples, labels.toList())

    fun seeLabelError(text: String) = seeText(form.find(".field-error"), text)
}
