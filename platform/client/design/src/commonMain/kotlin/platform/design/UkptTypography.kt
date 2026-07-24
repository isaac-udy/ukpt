package platform.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale, as a fixed set of roles.
 *
 * Built with [from] so the scale is defined once against whichever [UkptFonts] the theme carries —
 * a project swaps typefaces without restating every size and weight.
 *
 * The scale is deliberately short. A role that does not exist cannot be misused, and a screen that
 * "needs" a sixth size usually needs one of these five plus a colour or spacing change.
 */
@Immutable
data class UkptTypography(
    /** One per screen at most: the thing you read first. */
    val display: TextStyle,
    /** Section and card headings. */
    val title: TextStyle,
    /** Running text. The default for anything unmarked. */
    val body: TextStyle,
    /** Buttons and form labels: short, and usually adjacent to a control. */
    val label: TextStyle,
    /** Supporting detail, timestamps, footnotes. */
    val caption: TextStyle,
) {
    companion object {
        fun from(fonts: UkptFonts): UkptTypography = UkptTypography(
            display = TextStyle(
                fontFamily = fonts.display,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            title = TextStyle(
                fontFamily = fonts.display,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            body = TextStyle(
                fontFamily = fonts.body,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
            ),
            label = TextStyle(
                fontFamily = fonts.body,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
            caption = TextStyle(
                fontFamily = fonts.body,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}
