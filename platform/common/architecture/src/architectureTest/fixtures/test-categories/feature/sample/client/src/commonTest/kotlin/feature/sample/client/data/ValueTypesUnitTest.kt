package feature.sample.client.data

import feature.sample.client.ui.SamplePageDestination
import io.ktor.http.HttpStatusCode
import platform.testing.UnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

@UnitTest
class ValueTypesUnitTest {

    @Test
    fun valueTypesAreTestData() {
        val repository = SampleRepository(source = { "sample" })
        val destination = SamplePageDestination(page = 2)
        val tally = SampleTally()
        assertEquals(HttpStatusCode.OK.value, 200)
    }
}
