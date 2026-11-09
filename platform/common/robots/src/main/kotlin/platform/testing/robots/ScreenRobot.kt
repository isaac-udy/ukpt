package platform.testing.robots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists

/**
 * One screen's user actions (`click…`, `enter…`) and what the user sees (`see…`). Only robots find
 * nodes; each robot finds only its own screen's tags.
 */
@OptIn(ExperimentalTestApi::class)
abstract class ScreenRobot(context: RobotContext) {
    private val ui = context.ui

    protected fun click(tag: String) {
        ui.waitUntilExactlyOneExists(hasTestTag(tag), ROBOT_TIMEOUT_MILLIS)
        ui.onNodeWithTag(tag).performClick()
    }

    protected fun seeText(tag: String, text: String) {
        try {
            ui.waitUntil(timeoutMillis = ROBOT_TIMEOUT_MILLIS) { textOf(tag) == text }
        } catch (_: ComposeTimeoutException) {
            throw AssertionError("Expected $tag to show \"$text\"; it shows ${textOf(tag)?.let { "\"$it\"" } ?: "nothing"}")
        }
    }

    private fun textOf(tag: String): String? =
        ui.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().singleOrNull()
            ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
}
