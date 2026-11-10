package feature.sample.tests

import feature.sample.server.web.SamplePaths
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test

@SubcutaneousTest
class OtherPagesTest {

    @Test
    fun `the home page has a heading`() = subcutaneous(sampleTestGraph) {
        asUser(SamplePaths.HOME) {
            onTwoEntryPage { seeHeading("Samples") }
            onMisplacedPage { seeHeading("Samples") }
        }
    }
}
