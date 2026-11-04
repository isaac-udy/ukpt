package feature.sample.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class UncategorisedTest {

    @Test
    fun hasNoCategory() {
        assertEquals("", SampleState().label)
    }
}

class SampleHelper
