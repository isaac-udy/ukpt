package feature.sample.client.ui

import platform.testing.ComponentTest
import kotlin.test.Test
import kotlin.test.assertTrue

// Mentions UntestedHtmlScreenContent( only in a comment.
@ComponentTest
class SampleHtmlScreenContentTest {

    @Test
    fun rendersWithAViewModel() {
        val viewModel = SampleViewModel()
        SampleHtmlScreenContent(state = viewModel.state.value)
        assertTrue(true)
    }
}
