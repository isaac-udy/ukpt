package feature.sample.tests

import dev.isaacudy.udytils.htmlsnapshot.HtmlSnapshots
import feature.sample.server.web.SampleForm
import feature.sample.server.web.SamplePageState
import feature.sample.server.web.samplePage
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import platform.testing.SnapshotTest
import kotlin.test.Test

@SnapshotTest
class MisplacedSnapshotTest {

    private val snapshots = HtmlSnapshots()

    @Test
    fun empty() {
        val state = SamplePageState(emptyList(), SampleForm.Empty, errors = null)
        snapshots.assertMatches("SamplePage/empty", createHTML().html { samplePage(state) })
    }
}
