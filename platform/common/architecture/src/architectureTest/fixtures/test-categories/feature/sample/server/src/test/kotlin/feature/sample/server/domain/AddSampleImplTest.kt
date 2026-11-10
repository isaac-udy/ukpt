package feature.sample.server.domain

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import platform.testing.UnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

@UnitTest
class AddSampleImplTest {

    private val saved = mutableListOf<String>()

    @Test
    fun `a new label is saved trimmed`() = runTest {
        AddSampleImpl(flowOfSamples = { flowOf(emptyList()) }, saveSample = { saved += it })(" a ")

        checkSaved("a")
    }

    @Test
    fun `a label saved before is not saved again`() = runTest {
        AddSampleImpl(flowOfSamples = { flowOf(listOf("a")) }, saveSample = { saved += it })("a")

        checkSaved()
    }

    private fun checkSaved(vararg labels: String) {
        assertEquals(labels.toList(), saved)
    }
}
