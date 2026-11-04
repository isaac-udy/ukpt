package com.sample

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import platform.testing.EndToEndTest
import kotlin.test.Test

@EndToEndTest
class ScreenOnlyEndToEndTest {

    @Test
    fun `renders one screen`() = runComposeUiTest {
        // Names AppContent() and inProcessServer(graph) only in this comment.
        setContent { SampleScreen() }
        onNodeWithText("Sample").assertExists()
    }
}
