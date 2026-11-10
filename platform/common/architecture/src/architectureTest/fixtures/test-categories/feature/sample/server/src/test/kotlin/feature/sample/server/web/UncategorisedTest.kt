package feature.sample.server.web

import kotlin.test.Test
import kotlin.test.assertEquals

class UncategorisedTest {

    @Test
    fun `an empty form has no label`() {
        assertEquals("", SampleForm.Empty.label)
    }
}

class SampleHelper
