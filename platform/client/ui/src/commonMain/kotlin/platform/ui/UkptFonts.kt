package platform.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily

/**
 * The typeface slots the type scale is built from.
 *
 * Slots are roles, not typeface names, so [UkptTypography] can be built against any concrete set.
 *
 * [System] is the placeholder: it uses the platform's own faces so the module renders and
 * snapshots without shipping a typeface it has no licence for. A real project replaces it by
 * bundling variable TTFs under `src/commonMain/composeResources/font/` (lowercase filenames, with
 * the licence and source noted alongside them) and loading explicit weights from the variable file.
 *
 * Bundled compose-resources fonts *do* load under Paparazzi — doc surfaces render the real
 * typeface, so there is no need for a "pass System fonts in tests" convention.
 */
@Immutable
data class UkptFonts(
    /** Large, low-frequency text: display and title roles. */
    val display: FontFamily,
    /** Running text and UI labels. */
    val body: FontFamily,
    /** Code, identifiers, and anything that must align in columns. */
    val mono: FontFamily,
) {
    companion object {
        val System: UkptFonts = UkptFonts(
            display = FontFamily.Default,
            body = FontFamily.Default,
            mono = FontFamily.Monospace,
        )
    }
}
