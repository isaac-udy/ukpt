package feature.capture.insta.server.data

import feature.capture.insta.server.data.InstaFileStore
import platform.testing.AdapterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@AdapterTest
class InstaFileStoreAdapterTest {

    @Test
    fun readsTheFiles() {
        assertEquals(emptyList(), InstaFileStore().names())
    }
}
