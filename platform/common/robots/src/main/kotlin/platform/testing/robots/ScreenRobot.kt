package platform.testing.robots

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect

/**
 * One screen's user actions (`click…`, `enter…`) and what the user sees (`see…`). Only robots find
 * nodes; each robot finds only its own screen's tags.
 */
@OptIn(ExperimentalTestApi::class)
abstract class ScreenRobot(context: RobotContext) {
    private val ui = context.ui

    protected fun click(tag: String) {
        node(tag).performClick()
    }

    protected fun seeText(tag: String, text: String) {
        try {
            ui.waitUntil(timeoutMillis = ROBOT_TIMEOUT_MILLIS) { textOf(tag) == text }
        } catch (_: ComposeTimeoutException) {
            throw AssertionError("Expected $tag to show \"$text\"; it shows ${textOf(tag)?.let { "\"$it\"" } ?: "nothing"}")
        }
    }

    protected fun see(tag: String) {
        node(tag).assertIsDisplayed()
    }

    protected fun seeNo(tag: String) {
        try {
            ui.waitUntilDoesNotExist(hasTestTag(tag), ROBOT_TIMEOUT_MILLIS)
        } catch (_: ComposeTimeoutException) {
            throw AssertionError("Expected no $tag; it shows ${textOf(tag)?.let { "\"$it\"" } ?: "without text"}")
        }
    }

    protected fun seeEnabled(tag: String) {
        node(tag).assertIsEnabled()
    }

    protected fun seeDisabled(tag: String) {
        node(tag).assertIsNotEnabled()
    }

    protected fun enterText(tag: String, text: String) {
        node(tag).performTextInput(text)
    }

    protected fun clearText(tag: String) {
        node(tag).performTextClearance()
    }

    /** The editable field inside [within], for a text field primitive whose modifier lands on its frame. */
    protected fun enterTextWithin(within: String, text: String) {
        val matcher = hasSetTextAction() and hasAnyAncestor(hasTestTag(within))
        ui.waitUntilExactlyOneExists(matcher, ROBOT_TIMEOUT_MILLIS)
        ui.onNode(matcher).performTextInput(text)
    }

    protected fun scrollTo(tag: String) {
        node(tag).performScrollTo()
    }

    protected fun clickCentre(tag: String) {
        node(tag).performTouchInput { click(center) }
    }

    protected fun seeContentDescription(tag: String, description: String) {
        awaitMatching(tag, "to read \"$description\"") { config -> config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") == description }
    }

    protected fun seeProgress(tag: String, progress: ProgressBarRangeInfo) {
        awaitMatching(tag, "to show progress $progress") { config -> config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == progress }
    }

    protected fun seeSizeAtLeast(tag: String, width: Dp, height: Dp) {
        node(tag).assertIsDisplayed().assertWidthIsAtLeast(width).assertHeightIsAtLeast(height)
    }

    protected fun boundsOf(tag: String): DpRect = node(tag).getBoundsInRoot()

    /** A node inside [within] whose content description contains [description], such as a data table row. */
    protected fun clickDescribed(within: String, description: String) {
        described(within, description).performClick()
    }

    protected fun seeDescribed(within: String, description: String) {
        described(within, description).assertIsDisplayed()
    }

    /** A described control a primitive draws without a modifier slot, such as a stepper's −1. */
    protected fun seeDescribedEnabled(within: String, description: String) {
        described(within, description).assertIsEnabled()
    }

    protected fun seeDescribedDisabled(within: String, description: String) {
        described(within, description).assertIsNotEnabled()
    }

    protected fun seeDescribedSizeAtLeast(within: String, description: String, width: Dp, height: Dp) {
        described(within, description).assertIsDisplayed().assertWidthIsAtLeast(width).assertHeightIsAtLeast(height)
    }

    /** Text a design primitive draws inside [within] without a tag of its own, such as an empty state's message. */
    protected fun seeTextWithin(within: String, text: String) {
        withText(within, text).assertIsDisplayed()
    }

    protected fun seeNoTextWithin(within: String, text: String) {
        node(within)
        try {
            ui.waitUntilDoesNotExist(hasText(text) and hasAnyAncestor(hasTestTag(within)), ROBOT_TIMEOUT_MILLIS)
        } catch (_: ComposeTimeoutException) {
            throw AssertionError("Expected $within not to show \"$text\"")
        }
    }

    protected fun clickTextWithin(within: String, text: String) {
        withText(within, text).performClick()
    }

    /** One of the texts a merged node such as a list row reads, such as the row's date. */
    protected fun seeTextPart(tag: String, text: String) {
        awaitMatching(tag, "to include \"$text\"") { config -> config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
    }

    protected fun seeNoTextPart(tag: String, text: String) {
        awaitMatching(tag, "not to include \"$text\"") { config -> config.getOrNull(SemanticsProperties.Text)?.none { it.text == text } ?: true }
    }

    private fun node(tag: String): SemanticsNodeInteraction {
        ui.waitUntilExactlyOneExists(hasTestTag(tag), ROBOT_TIMEOUT_MILLIS)
        return ui.onNodeWithTag(tag)
    }

    private fun described(within: String, description: String): SemanticsNodeInteraction {
        val matcher = hasContentDescription(description, substring = true) and hasAnyAncestor(hasTestTag(within))
        try {
            ui.waitUntilExactlyOneExists(matcher, ROBOT_TIMEOUT_MILLIS)
        } catch (_: ComposeTimeoutException) {
            throw AssertionError("Expected one node in $within reading \"$description\"")
        }
        val node = ui.onNode(matcher)
        val scrollable = ui.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()
        return if (scrollable) node.performScrollTo() else node
    }

    private fun withText(within: String, text: String): SemanticsNodeInteraction {
        val matcher = hasText(text) and hasAnyAncestor(hasTestTag(within))
        try {
            ui.waitUntilExactlyOneExists(matcher, ROBOT_TIMEOUT_MILLIS)
        } catch (_: ComposeTimeoutException) {
            throw AssertionError("Expected $within to show \"$text\"")
        }
        return ui.onNode(matcher)
    }

    private fun awaitMatching(tag: String, expectation: String, predicate: (SemanticsConfiguration) -> Boolean) {
        val matcher = hasTestTag(tag) and SemanticsMatcher(expectation) { predicate(it.config) }
        try {
            ui.waitUntilExactlyOneExists(matcher, ROBOT_TIMEOUT_MILLIS)
        } catch (_: ComposeTimeoutException) {
            throw AssertionError("Expected $tag $expectation")
        }
    }

    private fun textOf(tag: String): String? =
        ui.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().singleOrNull()
            ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
}
