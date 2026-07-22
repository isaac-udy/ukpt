package platform.snapshot

import dev.isaacudy.udytils.snapshot.PreviewSnapshotCase
import dev.isaacudy.udytils.snapshot.PreviewSnapshotTestCase
import dev.isaacudy.udytils.snapshot.PreviewSnapshots
import org.junit.runners.Parameterized

/**
 * Snapshot-tests every `@Preview` composable in this module.
 *
 * The harness lives in `dev.isaacudy.udytils:snapshot`; the scanned package tree below is the
 * only per-module fact — each feature's client module scans its own `feature.[name]` tree.
 */
class PreviewSnapshotTest(
    case: PreviewSnapshotCase,
) : PreviewSnapshotTestCase(case) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<PreviewSnapshotCase> = PreviewSnapshots.scan("feature.ukpt")
    }
}
