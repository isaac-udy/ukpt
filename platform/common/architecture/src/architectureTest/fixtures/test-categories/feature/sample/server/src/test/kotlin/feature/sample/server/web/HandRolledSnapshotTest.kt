package feature.sample.server.web

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import platform.testing.SnapshotTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@SnapshotTest
class HandRolledSnapshotTest {

    @Test
    fun empty() {
        val html = createHTML().html { samplePage(SamplePageState(emptyList(), SampleForm.Empty, errors = null)) }

        assertEquals(File("src/test/snapshots/html/SamplePage/empty.html").readText(), html)
    }
}
