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
 *
 * The scanned package tree is the ONE line that differs per module: each feature's client module
 * scans its own `feature.[name]` tree.
 *
 * Goldens are directory-grouped by the preview's declaring package and function name (see
 * [PreviewCase] / [DirectorySnapshotHandler]) rather than the stock long flat filename, e.g.
 * `snapshots/images/feature/ukpt/ui/UkptScreenPreview.png`.
 */
@RunWith(Parameterized::class)
class PreviewSnapshotTest(
    private val case: PreviewCase,
) {

    /**
     * One discovered preview plus its resolved golden path. [toString] is the JUnit parameterized
     * display name (`feature.ukpt.ui.UkptScreenPreview`), matching the golden path identity.
     */
    class PreviewCase(
        val preview: ComposablePreview<AndroidPreviewInfo>,
        /** POSIX-relative golden path under `snapshots/images/`, without the `.png` extension. */
        val goldenPath: String,
        private val displayName: String,
    ) {
        override fun toString(): String = displayName
    }

    companion object {
        private val cases: List<PreviewCase> by lazy {
            AndroidComposablePreviewScanner()
                .scanPackageTrees("feature.ukpt")
                .includePrivatePreviews()
                .getPreviews()
                .map { it.toPreviewCase() }
                .also { assertNoGoldenPathCollisions(it) }
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<PreviewCase> = cases
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = SnapshotRule.desktopDeviceConfig,
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        snapshotHandler = DirectorySnapshotHandler(),
    )

    @Test
    fun snapshot() {
        paparazzi.snapshot(name = case.goldenPath) {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                case.preview()
            }
        }
    }
}

/**
 * Resolves a discovered preview's directory-grouped golden path and display name.
 *
 * - Package directories come from the preview's declaring class (e.g.
 *   `feature.ukpt.ui.UkptScreenKt` → `feature/ukpt/ui`), dropping the synthetic file class
 *   (`…ScreenKt`) — the package + function name is the identity.
 * - The file base is the preview's function name plus, for any non-default `@Preview` qualifiers
 *   (a `name` argument, `fontScale`, `uiMode`, `device`, …), the suffix
 *   [AndroidPreviewScreenshotIdBuilder] renders — so several `@Preview`s stacked on one function
 *   stay distinct. `ignoreClassName()` keeps just `<function>[.<qualifiers>]`.
 */
internal fun ComposablePreview<AndroidPreviewInfo>.toPreviewCase(): PreviewSnapshotTest.PreviewCase {
    val packageDirs = declaringClass.substringBeforeLast('.', missingDelimiterValue = "")
        .replace('.', '/')
    val fileBase = AndroidPreviewScreenshotIdBuilder(this)
        .ignoreClassName()
        .build()
    val goldenPath = if (packageDirs.isEmpty()) fileBase else "$packageDirs/$fileBase"
    val packageName = declaringClass.substringBeforeLast('.', missingDelimiterValue = "")
    val displayName = if (packageName.isEmpty()) fileBase else "$packageName.$fileBase"
    return PreviewSnapshotTest.PreviewCase(
        preview = this,
        goldenPath = goldenPath,
        displayName = displayName,
    )
}

/**
 * Fails fast at parameter-creation time if two discovered previews map to the same golden path
 * (which would silently overwrite one another), naming every colliding preview so it can be renamed.
 */
internal fun assertNoGoldenPathCollisions(cases: List<PreviewSnapshotTest.PreviewCase>) {
    val collisions = cases
        .groupBy { it.goldenPath }
        .filterValues { it.size > 1 }
    if (collisions.isNotEmpty()) {
        val detail = collisions.entries.joinToString("\n") { (path, colliding) ->
            val fns = colliding.joinToString(", ") { "${it.preview.declaringClass}#${it.preview.methodName}" }
            "  $path.png <- $fns"
        }
        error(
            "Preview snapshot golden-path collision: ${collisions.size} path(s) claimed by more than " +
                "one @Preview. Rename the colliding preview function(s):\n$detail",
        )
    }
}
