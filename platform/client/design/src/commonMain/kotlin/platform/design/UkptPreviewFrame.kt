package platform.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The viewport-sized frame a screen `@Preview` renders inside, so its golden reads as a screenshot
 * of the app on the project's primary form factor rather than a render on the snapshot harness's
 * canvas.
 *
 * The harness canvas is a deliberately oversized square (960 dp per axis) whose only job is to
 * never be the constraint. Without a frame, a screen preview is either stretched over that square
 * (`fillMaxSize`) or padded out to it with dead canvas — neither looks like the app on a device.
 * Each module's `PreviewSnapshotTest` renders in `RenderingMode.SHRINK`, which crops the golden to
 * this frame, so the PNG is exactly [width] x [height].
 *
 * The frame pins the palette via [UkptTheme] (a preview must not follow the system, or the golden
 * is nondeterministic) and measures [UkptTheme.viewport] from its own bounds, so viewport-adaptive
 * layouts answer as they would on the framed device.
 *
 * The default frame is a tall phone at [UkptViewport.Default]'s width. A project whose primary
 * form factor is something else should change the height default here in the same change that
 * moves [UkptViewport.Default] — the two describe the same device.
 */
@Composable
fun UkptPreviewFrame(
    colors: UkptColors,
    width: Dp = UkptViewport.Default.widthDp,
    height: Dp = 844.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.width(width).height(height)) {
        UkptTheme(colors = colors) {
            ProvideUkptViewport {
                content()
            }
        }
    }
}
