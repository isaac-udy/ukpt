package feature.sample.tests

import platform.testing.UnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

@UnitTest
class MisplacedUnitTest {

    @Test
    fun `labels are trimmed`() {
        assertEquals("a", " a ".trim())
    }
}
