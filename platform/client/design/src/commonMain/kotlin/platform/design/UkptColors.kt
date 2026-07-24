package platform.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The design system's semantic colour roles.
 *
 * Roles are named for the job they do, not the colour they are: a screen asks for `surface` and
 * `onSurface`, never for "grey 100". That is what lets a palette swap without touching call sites,
 * and it is why a theme *is* a [UkptColors] instance rather than an enum of theme names.
 *
 * The [Light] and [Dark] palettes below are deliberately neutral placeholders — a greyscale with a
 * single restrained accent. They render honestly and are snapshot-tested, but they are not an
 * identity. Authoring the real palette is the first thing a project does with this module; the
 * `ukpt-design-system` skill drives that, including renaming the `Ukpt` prefix to the project's own.
 */
@Immutable
data class UkptColors(
    /** The page behind everything. */
    val background: Color,
    /** Raised areas that sit on [background]: cards, sheets, bars. */
    val surface: Color,
    /** Primary content on [surface] or [background]. */
    val onSurface: Color,
    /** Secondary content: supporting text, inactive icons. Must stay legible on [surface]. */
    val onSurfaceVariant: Color,
    /** The one colour that draws the eye. Used for primary actions and selection. */
    val accent: Color,
    /** Content on [accent]. */
    val onAccent: Color,
    /** Hairlines, dividers and borders. */
    val outline: Color,
    /** Destructive and failure states. */
    val error: Color,
    /** Content on [error]. */
    val onError: Color,
) {
    companion object {
        val Light: UkptColors = UkptColors(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF4F4F5),
            onSurface = Color(0xFF18181B),
            onSurfaceVariant = Color(0xFF52525B),
            accent = Color(0xFF3F3F46),
            onAccent = Color(0xFFFAFAFA),
            outline = Color(0xFFD4D4D8),
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
        )

        val Dark: UkptColors = UkptColors(
            background = Color(0xFF09090B),
            surface = Color(0xFF18181B),
            onSurface = Color(0xFFFAFAFA),
            onSurfaceVariant = Color(0xFFA1A1AA),
            accent = Color(0xFFE4E4E7),
            onAccent = Color(0xFF18181B),
            outline = Color(0xFF3F3F46),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
        )
    }
}
