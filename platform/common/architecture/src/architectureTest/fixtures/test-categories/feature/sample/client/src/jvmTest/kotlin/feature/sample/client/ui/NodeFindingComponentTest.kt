package feature.sample.client.ui

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import feature.sample.tests.onSampleScreen
import platform.testing.ComponentTest
import platform.testing.robots.asUser
import kotlin.test.Test

@ComponentTest
class NodeFindingComponentTest {

    @Test
    fun `save is clickable`() = runComposeUiTest {
        setContent { SampleScreenContent(SampleState(label = "Saved")) }
        onNodeWithTag("SampleScreen.SaveButton").performClick()
        asUser {
            onSampleScreen { seeLabel("Saved") }
        }
    }
}
