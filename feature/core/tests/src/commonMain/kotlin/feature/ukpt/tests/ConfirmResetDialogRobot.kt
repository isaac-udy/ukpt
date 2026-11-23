package feature.ukpt.tests

import feature.ukpt.client.ui.ConfirmResetDestination
import feature.ukpt.client.ui.ConfirmResetDialogTags
import platform.testing.robots.RobotContext
import platform.testing.robots.ScreenRobot
import platform.testing.robots.User

fun User.onConfirmResetDialog(block: ConfirmResetDialogRobot.() -> Unit) =
    on(ConfirmResetDestination::class, ConfirmResetDialogTags.Dialog, ::ConfirmResetDialogRobot, block)

class ConfirmResetDialogRobot internal constructor(context: RobotContext) : ScreenRobot(context) {
    fun clickResetButton() = click(ConfirmResetDialogTags.ResetButton)
    fun clickCancelButton() = click(ConfirmResetDialogTags.CancelButton)
}
