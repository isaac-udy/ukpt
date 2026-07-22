package platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import platform.ui.UkptShapes
import platform.ui.UkptSpacing
import platform.ui.UkptTheme

/**
 * How much weight a button carries. Growth means a new entry here, never a new button composable —
 * see `design-system/principles.md`.
 */
enum class UkptButtonVariant {
    /** The one action the screen wants. At most one per view. */
    Primary,

    /** A real alternative to [Primary]: "Cancel" next to "Save". */
    Secondary,

    /** Low-emphasis, usually repeated: a row action, a toolbar item. */
    Ghost,
}

/**
 * The button.
 *
 * Stateless: it renders [label] and reports [onClick]. It owns no pressed/loading/selected state,
 * because the caller already has that state and a component that hides it forces a second source of
 * truth.
 *
 * Every value it draws with resolves to a token. A variant that needs a colour the palette does not
 * have is a signal the palette is missing a role, not that this file should hold a literal.
 */
@Composable
fun UkptButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: UkptButtonVariant = UkptButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val colors = UkptTheme.colors
    val container = when (variant) {
        UkptButtonVariant.Primary -> colors.accent
        UkptButtonVariant.Secondary -> colors.surface
        UkptButtonVariant.Ghost -> Color.Transparent
    }
    val content = when (variant) {
        UkptButtonVariant.Primary -> colors.onAccent
        UkptButtonVariant.Secondary -> colors.onSurface
        UkptButtonVariant.Ghost -> colors.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(UkptShapes.small)
            .background(if (enabled) container else colors.surface)
            .then(
                if (variant == UkptButtonVariant.Secondary) {
                    // Hairline: the one raw dimension in the system, since a 1dp border is a
                    // rendering floor rather than a spacing decision.
                    Modifier.border(1.dp, colors.outline, UkptShapes.small)
                } else {
                    Modifier
                },
            )
            // `role` is not optional: a bespoke clickable Box announces nothing to a screen reader
            // without it, so the control would be invisible to assistive technology while looking
            // perfectly fine.
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = UkptSpacing.md, vertical = UkptSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = UkptTheme.typography.label,
            color = if (enabled) content else colors.onSurfaceVariant,
        )
    }
}
