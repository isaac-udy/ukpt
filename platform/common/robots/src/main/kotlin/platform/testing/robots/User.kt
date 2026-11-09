package platform.testing.robots

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.enro.NavigationKey
import dev.enro.context.RootContext
import dev.enro.context.activeLeafDestination
import kotlin.reflect.KClass

internal const val ROBOT_TIMEOUT_MILLIS = 10_000L

/**
 * Drives the UI as a user does, one screen at a time. With [navigation], `on<Screen> { }` also
 * waits until the screen's destination is the active one, so a step can't act on a screen behind
 * a dialog.
 */
@ExperimentalTestApi
fun ComposeUiTest.asUser(navigation: RootContext? = null, block: User.() -> Unit) {
    User(RobotContext(this), navigation).block()
}

/** What a [ScreenRobot] drives. Only `platform.testing.robots` reads it. */
@OptIn(ExperimentalTestApi::class)
class RobotContext internal constructor(internal val ui: ComposeUiTest)

@OptIn(ExperimentalTestApi::class)
class User internal constructor(
    private val context: RobotContext,
    private val navigation: RootContext?,
) {
    fun <K : NavigationKey, R : ScreenRobot> on(
        destination: KClass<K>,
        screenTag: String,
        robot: (RobotContext) -> R,
        block: R.() -> Unit,
    ) {
        context.ui.waitForIdle()
        if (navigation != null) awaitActive(navigation, destination)
        context.ui.waitUntilExactlyOneExists(hasTestTag(screenTag), ROBOT_TIMEOUT_MILLIS)
        robot(context).block()
    }

    private fun awaitActive(navigation: RootContext, destination: KClass<*>) {
        try {
            context.ui.waitUntil(timeoutMillis = ROBOT_TIMEOUT_MILLIS) { destination.isInstance(navigation.activeLeafDestination()?.key) }
        } catch (_: ComposeTimeoutException) {
            val active = navigation.activeLeafDestination()?.key?.let { it::class.simpleName } ?: "none"
            throw AssertionError("Expected ${destination.simpleName} to be the active screen; the active screen is $active")
        }
    }
}
