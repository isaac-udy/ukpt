package feature.sample.tests

import feature.sample.server.data.SampleRepository
import feature.sample.server.web.SamplePaths
import feature.sample.server.web.SampleRoutes
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import org.jsoup.Jsoup
import platform.server.web.WebRoutes
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SubcutaneousTest
class ShortcutSubcutaneousTest {

    @Test
    fun `posting a label stores it`() = subcutaneous(sampleTestGraph) {
        // Names asUser(SamplePaths.HOME) only in this comment.
        assertTrue(server<WebRoutes>() is SampleRoutes)
        val page = Jsoup.parse(client.post(SamplePaths.SAMPLES).bodyAsText())

        assertEquals(1, page.select("li").size)
        assertEquals(listOf("a"), server<SampleRepository>().flowOfSamples().first())
    }
}
