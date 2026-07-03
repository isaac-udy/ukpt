package platform.snapshot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Snapshot-tests every `@Preview` composable in this module. Previews are discovered from the
 * compiled classes at test time (ComposablePreviewScanner), so adding a `@Preview` to a composable
 * is all that is needed to snapshot it — no hand-written test per state.
 */
@RunWith(Parameterized::class)
class PreviewSnapshotTest(
    private val preview: ComposablePreview<AndroidPreviewInfo>,
) {

    companion object {
        private val previews: List<ComposablePreview<AndroidPreviewInfo>> by lazy {
            AndroidComposablePreviewScanner()
                .scanPackageTrees("feature.ukpt")
                .includePrivatePreviews()
                .getPreviews()
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> = previews
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = SnapshotRule.desktopDeviceConfig,
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
    )

    @Test
    fun snapshot() {
        paparazzi.snapshot(name = AndroidPreviewScreenshotIdBuilder(preview).build()) {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                preview()
            }
        }
    }
}
