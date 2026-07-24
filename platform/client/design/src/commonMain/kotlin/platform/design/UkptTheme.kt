package platform.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalUkptColors = staticCompositionLocalOf { UkptColors.Light }
private val LocalUkptTypography = staticCompositionLocalOf { UkptTypography.from(UkptFonts.System) }
private val LocalUkptFonts = staticCompositionLocalOf { UkptFonts.System }

/**
 * Installs a palette and typeface set, and wraps a [MaterialTheme] derived from them.
 *
 * The MaterialTheme wrapper is not optional decoration. Raw material3 internals — text-field
 * decoration, dividers, `LocalContentColor`, ripple — read the material theme, so without it those
 * details land on material's defaults and quietly disagree with the tokens. Wrapping here means
 * call sites never wrap twice.
 *
 * [colors] has no default **on purpose**: the sibling overload defaults everything, so giving this
 * one a default would make a bare `UkptTheme { }` ambiguous. You reach for this overload precisely
 * when you want to force a palette or inject fonts.
 */
@Composable
fun UkptTheme(
    colors: UkptColors,
    fonts: UkptFonts = UkptFonts.System,
    content: @Composable () -> Unit,
) {
    val typography = remember(fonts) { UkptTypography.from(fonts) }
    CompositionLocalProvider(
        LocalUkptColors provides colors,
        LocalUkptFonts provides fonts,
        LocalUkptTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.background,
                onBackground = colors.onSurface,
                surface = colors.surface,
                onSurface = colors.onSurface,
                surfaceVariant = colors.surface,
                onSurfaceVariant = colors.onSurfaceVariant,
                outline = colors.outline,
                error = colors.error,
                onError = colors.onError,
            ),
            typography = Typography(
                displaySmall = typography.display,
                titleLarge = typography.title,
                bodyLarge = typography.body,
                labelLarge = typography.label,
                bodySmall = typography.caption,
            ),
            content = content,
        )
    }
}

/**
 * The spec-friendly form: follow the system's light/dark setting unless told otherwise.
 *
 * This is what an app root normally uses. Pass [dark] explicitly to force a palette in a preview or
 * a doc surface.
 */
@Composable
fun UkptTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    UkptTheme(
        colors = if (dark) UkptColors.Dark else UkptColors.Light,
        content = content,
    )
}

/**
 * Reads the tokens in effect.
 *
 * This is the **only** way feature code should reach tokens — `UkptTheme.colors.accent`, never a
 * literal and never a direct `UkptColors.Light` reference, which would ignore the active palette.
 */
object UkptTheme {
    val colors: UkptColors
        @Composable @ReadOnlyComposable get() = LocalUkptColors.current

    val typography: UkptTypography
        @Composable @ReadOnlyComposable get() = LocalUkptTypography.current

    val fonts: UkptFonts
        @Composable @ReadOnlyComposable get() = LocalUkptFonts.current

    /** Requires an enclosing [ProvideUkptViewport]; falls back to [UkptViewport.Default]. */
    val viewport: UkptViewport
        @Composable @ReadOnlyComposable get() = LocalUkptViewport.current
}
