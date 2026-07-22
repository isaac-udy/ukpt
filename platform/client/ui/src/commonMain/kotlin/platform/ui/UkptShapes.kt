package platform.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The corner language.
 *
 * Like [UkptSpacing], a bare object rather than a theme-scoped holder: shape is part of the
 * identity's constant vocabulary, not something a palette swap should change.
 *
 * Three steps, chosen by the size of the thing being shaped rather than by taste — a chip and a
 * dialog should not share a radius, because the same absolute radius reads as much rounder on a
 * small element.
 */
object UkptShapes {
    /** Chips, badges, inputs, buttons. */
    val small: Shape = RoundedCornerShape(6.dp)
    /** Cards and list rows. */
    val medium: Shape = RoundedCornerShape(10.dp)
    /** Sheets, dialogs, and other full surfaces. */
    val large: Shape = RoundedCornerShape(16.dp)
}
