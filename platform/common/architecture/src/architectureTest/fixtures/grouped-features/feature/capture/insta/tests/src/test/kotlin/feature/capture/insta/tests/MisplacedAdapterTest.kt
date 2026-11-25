package feature.capture.insta.tests

import feature.capture.insta.server.data.InstaFileStore
import platform.testing.AdapterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@AdapterTest
class MisplacedAdapterTest {

    @Test
    fun readsTheFiles() {
        assertEquals(emptyList(), InstaFileStore().names())
    }
}
