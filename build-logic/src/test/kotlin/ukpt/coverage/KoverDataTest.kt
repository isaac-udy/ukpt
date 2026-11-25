package ukpt.coverage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class KoverDataTest {

    @Test
    fun aTaskThatWroteTestResultsButNoIcFileIsReported() {
        val root = Files.createTempDirectory("kover-data").toFile()
        try {
            val results = root.resolve("results").apply { mkdirs() }
            results.resolve("unitTest").apply { mkdirs() }.resolve("TEST-a.UnitTest.xml").writeText("<testsuite/>")
            results.resolve("subcutaneousAndroidHostTest").apply { mkdirs() }.resolve("TEST-a.SubcutaneousTest.xml").writeText("<testsuite/>")
            results.resolve("checkTest").mkdirs()
            val ic = root.resolve("unitTest.ic").apply { writeText("") }
            val artifact = KoverArtifact(":a", "jvm", emptyList(), emptyList(), listOf(ic))

            val problems = uninstrumentedTasks(
                listOf(artifact),
                listOf("unitTest", "subcutaneousAndroidHostTest", "checkTest").associate { ":a|$it" to results.resolve(it).path },
            )

            assertEquals(
                listOf("`:a:subcutaneousAndroidHostTest` ran tests but Kover wrote no coverage data for it; the task's coverage is missing from the report."),
                problems,
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
