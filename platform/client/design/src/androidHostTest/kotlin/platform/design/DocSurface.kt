package platform.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The root container every design-system doc surface renders inside.
 *
 * Doc surfaces are `@Preview` composables discovered by `PreviewSnapshotTest`, which renders this
 * module in `RenderingMode.SHRINK`: the golden is cropped to this container, so [width] and
 * [height] set the exact canvas of the PNG the doc page embeds (the preview pipeline's default
 * NORMAL mode would instead pad every sheet out to the shared 960 dp device canvas). The fixed
 * size is also what gives the sheet bounded constraints, so `fillMaxSize()` resolves against the
 * container rather than the device.
 */
@Composable
internal fun DocSurface(
    colors: UkptColors,
    width: Dp,
    height: Dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.width(width).height(height)) {
        UkptTheme(colors = colors) { content() }
    }
}
