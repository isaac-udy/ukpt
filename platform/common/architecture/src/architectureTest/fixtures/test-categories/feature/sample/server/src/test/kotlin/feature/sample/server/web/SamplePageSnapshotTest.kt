package feature.sample.server.web

import dev.isaacudy.udytils.htmlsnapshot.HtmlSnapshots
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import platform.testing.SnapshotTest
import kotlin.test.Test

@SnapshotTest
class SamplePageSnapshotTest {

    private val snapshots = HtmlSnapshots()

    @Test
    fun empty() {
        snapshots.assertMatches("SamplePage/empty", render(SamplePageState(emptyList(), SampleForm.Empty, errors = null)))
    }

    @Test
    fun invalidLabel() {
        snapshots.assertMatches("SamplePage/invalidLabel", render(SamplePageState(emptyList(), SampleForm(label = ""), SampleForm.Errors(label = "Enter a label."))))
    }

    private fun render(state: SamplePageState): String = "<!DOCTYPE html>" + createHTML().html { samplePage(state) }
}
