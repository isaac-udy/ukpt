package feature.sample.tests

import feature.sample.client.ui.SampleDestination
import feature.sample.client.ui.SampleScreenTags
import platform.testing.robots.RobotContext
import platform.testing.robots.ScreenRobot
import platform.testing.robots.User

fun User.onSampleScreen(block: SampleScreenRobot.() -> Unit) =
    on(SampleDestination::class, SampleScreenTags.Screen, ::SampleScreenRobot, block)

class SampleScreenRobot internal constructor(context: RobotContext) : ScreenRobot(context) {
    fun clickSaveButton() = click(SampleScreenTags.SaveButton)
    fun seeLabel(text: String) = seeText(SampleScreenTags.Label, text)
}
