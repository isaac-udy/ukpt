package feature.sample.tests

import feature.sample.client.ui.OtherScreenTags
import feature.sample.client.ui.SampleScreenTags
import platform.testing.robots.RobotContext
import platform.testing.robots.ScreenRobot

// Names fun User.onTwoScreen( only in this comment.
class TwoScreenRobot internal constructor(context: RobotContext) : ScreenRobot(context) {
    fun save() = click(SampleScreenTags.SaveButton)
    fun clickOtherScreen() = click(OtherScreenTags.Screen)
}
