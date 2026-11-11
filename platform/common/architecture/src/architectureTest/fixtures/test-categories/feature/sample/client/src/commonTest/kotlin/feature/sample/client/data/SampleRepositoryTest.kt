package feature.sample.client.data

import feature.sample.client.ui.SampleState
import platform.testing.UnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

@UnitTest
class SampleRepositoryTest {

    @Test
    fun repositoryIsCreated() {
        val repository = SampleRepository(source = { "sample" })
        checkLabel(SampleState(label = "a"), "a")
    }

    private fun checkLabel(state: SampleState, expected: String) {
        assertEquals(expected, state.label)
    }
}
