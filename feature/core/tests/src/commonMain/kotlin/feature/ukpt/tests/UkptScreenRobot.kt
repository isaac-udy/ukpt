package feature.ukpt.tests

import feature.ukpt.client.ui.UkptDestination
import feature.ukpt.client.ui.UkptScreenTags
import platform.testing.robots.RobotContext
import platform.testing.robots.ScreenRobot
import platform.testing.robots.User

fun User.onUkptScreen(block: UkptScreenRobot.() -> Unit) =
    on(UkptDestination::class, UkptScreenTags.Screen, ::UkptScreenRobot, block)

class UkptScreenRobot internal constructor(context: RobotContext) : ScreenRobot(context) {
    fun clickGreetButton() = click(UkptScreenTags.GreetButton)
    fun clickResetButton() = click(UkptScreenTags.ResetButton)
    fun clickRetryButton() = click(UkptScreenTags.RetryButton)

    fun seeGreeting(text: String) = seeText(UkptScreenTags.LatestGreeting, text)
    fun seeNoGreetings() = seeText(UkptScreenTags.LatestGreeting, "No greetings yet")
    fun seeGreetingCount(count: Int) = seeText(UkptScreenTags.GreetingCount, "$count greetings in history")
}
