package platform.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the UI has, reduced to the one question layouts actually ask.
 *
 * Deliberately not a full breakpoint ladder: [isCompact] is a single boolean, so a layout either
 * has room or it doesn't. Multi-step breakpoints multiply the number of states every screen must be
 * designed and snapshotted in, and the middle steps are the ones nobody checks.
 */
@Immutable
data class UkptViewport(
    val widthDp: Dp,
    /** True when the UI is phone-width: prefer a single column and full-width controls. */
    val isCompact: Boolean,
) {
    companion object {
        /** Below this the UI is treated as compact. */
        val CompactMaxWidth: Dp = 600.dp

        fun of(widthDp: Dp): UkptViewport = UkptViewport(
            widthDp = widthDp,
            isCompact = widthDp < CompactMaxWidth,
        )

        /**
         * The value in effect when nothing has measured yet. Set to the project's primary form
         * factor so a composable rendered outside [ProvideUkptViewport] — a preview, a doc surface
         * — still reads a sensible viewport rather than a misleading one.
         */
        val Default: UkptViewport = of(390.dp)
    }
}

internal val LocalUkptViewport = staticCompositionLocalOf { UkptViewport.Default }

/**
 * Measures the available width and publishes it as [UkptTheme.viewport] to [content].
 *
 * Wrap this once at the app root, inside [UkptTheme]. Nesting it is legal — an inner call
 * re-measures for a pane — but a screen that reaches for it usually wants a plain layout instead.
 */
@Composable
fun ProvideUkptViewport(
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        CompositionLocalProvider(
            LocalUkptViewport provides UkptViewport.of(maxWidth),
            content = content,
        )
    }
}
