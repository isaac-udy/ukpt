package feature.sample.client.ui

import androidx.compose.ui.test.v2.runComposeUiTest
import feature.sample.tests.onSampleScreen
import platform.testing.ComponentTest
import platform.testing.robots.asUser
import kotlin.test.Test

@ComponentTest
class SampleScreenContentTest {

    @Test
    fun `the label shows`() = runComposeUiTest {
        // Names onNodeWithTag( and performClick( only in this comment.
        setContent { SampleScreenContent(SampleState(label = "Saved")) }
        asUser {
            onSampleScreen { seeLabel("Saved") }
        }
    }
}
