package platform.design

import androidx.compose.ui.unit.dp

/**
 * The spacing scale.
 *
 * A bare object, not a theme-scoped token holder and not a CompositionLocal: spacing is **one
 * density**, on purpose. Making it themeable invites per-screen density drift and a "compact mode"
 * nobody maintains. If a layout needs to adapt, it adapts through [UkptViewport] breakpoints —
 * changing *which* layout renders — not by shrinking every gap.
 *
 * The scale is geometric so that adjacent steps read as clearly different. Values between steps are
 * a smell: reach for the nearest step instead.
 */
object UkptSpacing {
    /** Hairline separation: icon to its label. */
    val xs = 4.dp
    /** Related items inside a single control. */
    val sm = 8.dp
    /** The default gap between elements, and the standard inset from a screen edge. */
    val md = 16.dp
    /** Between groups of related content. */
    val lg = 24.dp
    /** Between major sections. */
    val xl = 32.dp
    /** Around a lone focal element: an empty state, a dialog's content. */
    val xxl = 48.dp
}
