package feature.capture.insta.client.data

import platform.testing.AdapterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@AdapterTest
class InstaFileStoreAdapterTest {

    @Test
    fun readsTheDeviceFiles() {
        assertEquals(emptyList(), InstaFileStore().names())
    }
}
