package platform.snapshot

import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import dev.isaacudy.udytils.snapshot.PreviewSnapshotCase
import dev.isaacudy.udytils.snapshot.PreviewSnapshotTestCase
import dev.isaacudy.udytils.snapshot.PreviewSnapshots
import org.junit.runners.Parameterized

/**
 * Snapshot-tests every `@Preview` composable in this module — the design-system doc surfaces, and
 * any future preview under `platform.design`.
 *
 * Unlike the feature-module default (NORMAL, bounded to the shared 960 dp canvas), this module
 * renders in [RenderingMode.SHRINK]: every doc surface bounds itself with `DocSurface`'s
 * fixed-size root container, and SHRINK crops each golden to that container instead of padding it
 * out to the canvas.
 */
class PreviewSnapshotTest(
    case: PreviewSnapshotCase,
) : PreviewSnapshotTestCase(
    case = case,
    renderingMode = RenderingMode.SHRINK,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<PreviewSnapshotCase> = PreviewSnapshots.scan("platform.design")
    }
}
