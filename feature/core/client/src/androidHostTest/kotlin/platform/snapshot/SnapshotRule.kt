package platform.snapshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.Navigation
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen
import org.junit.rules.TestRule

/**
 * JUnit rule wrapping Paparazzi for snapshot-testing Compose UI without a device.
 *
 * - [screen] renders inside a fixed-size container (bounded constraints) — use it for screen
 *   content and any composable that uses `fillMaxWidth()`/`fillMaxSize()`.
 * - [component] renders at content size with padding — use it for small, self-sizing composables.
 *
 * Composables under test must be `internal` (not `private`) so this source set can reach them.
 *
 * The preview-driven [PreviewSnapshotTest] also reads [desktopDeviceConfig] from here.
 */
class SnapshotRule private constructor(
    private val paparazzi: Paparazzi,
) : TestRule by paparazzi {
    constructor() : this(
        paparazzi = Paparazzi(),
    )

    fun screen(
        name: String? = null,
        width: Dp = 960.dp,
        height: Dp = 960.dp,
        composable: @Composable () -> Unit,
    ) {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = desktopDeviceConfig,
            renderingMode = SessionParams.RenderingMode.V_SCROLL,
        )
        paparazzi.snapshot(name) {
            Box(modifier = Modifier.width(width).height(height)) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    composable()
                }
            }
        }
    }

    fun component(name: String? = null, composable: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = desktopDeviceConfig,
            renderingMode = SessionParams.RenderingMode.SHRINK,
        )
        paparazzi.snapshot(name) {
            Box(
                modifier = Modifier.padding(8.dp),
            ) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    composable()
                }
            }
        }
    }

    companion object {
        val desktopDeviceConfig = DeviceConfig(
            screenHeight = 1920,
            screenWidth = 1920,
            xdpi = 320,
            ydpi = 320,
            orientation = ScreenOrientation.LANDSCAPE,
            density = Density.create(320),
            ratio = ScreenRatio.LONG,
            size = ScreenSize.NORMAL,
            keyboard = Keyboard.NOKEY,
            touchScreen = TouchScreen.NOTOUCH,
            keyboardState = KeyboardState.HIDDEN,
            softButtons = false,
            navigation = Navigation.NONAV,
        )
    }
}
