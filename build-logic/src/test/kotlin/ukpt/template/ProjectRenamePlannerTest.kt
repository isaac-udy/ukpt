package ukpt.template

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ProjectRenamePlannerTest {
    @TempDir
    lateinit var repository: Path

    private val request = ProjectRenameRequest(
        projectName = "sample-app",
        packageName = "com.example.sample",
        typePrefix = "SampleApp",
    )

    @Test
    fun classifiesReplaceReviewAndKeepOccurrences() {
        write(
            "app/src/main/kotlin/com/isaacudy/ukpt/Main.kt",
            "package com.isaacudy.ukpt\nclass UkptApplication\nval key = \"ukpt.projectName\"",
        )
        write("app/build.gradle.kts", "plugins { id(\"ukpt.compose-library\") }")
        write("feature/core/Core.kt", "package feature.ukpt\nimport com.isaacudy.ukpt.App\nclass UkptScreen")
        write(".agents/skills/ukpt-example/SKILL.md", "name: ukpt-example")
        write("gradle.properties", "ukpt.projectName=ukpt")

        val plan = ProjectRenamePlanner.plan(repository, request)

        assertTrue(plan.occurrences.any {
            it.path.endsWith("Main.kt") && it.source == "com.isaacudy.ukpt" &&
                it.disposition == RenameDisposition.REPLACE
        })
        assertTrue(plan.occurrences.any {
            it.path.endsWith("Main.kt") && it.source == "ukpt" &&
                it.disposition == RenameDisposition.KEEP
        })
        assertTrue(plan.occurrences.any {
            it.path == "app/build.gradle.kts" && it.source == "ukpt" &&
                it.disposition == RenameDisposition.KEEP
        })
        assertTrue(plan.occurrences.any {
            it.path == "feature/core/Core.kt" && it.source == "feature.ukpt" &&
                it.disposition == RenameDisposition.REVIEW
        })
        assertTrue(plan.occurrences.any {
            it.path == "feature/core/Core.kt" && it.source == "com.isaacudy.ukpt" &&
                it.disposition == RenameDisposition.REPLACE
        })
        assertTrue(plan.occurrences.any {
            it.path.endsWith("Main.kt") && it.source == "Ukpt" && it.replacement == "SampleApp"
        })
        assertTrue(plan.occurrences.any {
            it.path.startsWith(".agents/") && it.disposition == RenameDisposition.KEEP
        })
        assertEquals(
            listOf(
                DirectoryMove(
                    "app/src/main/kotlin/com/isaacudy/ukpt",
                    "app/src/main/kotlin/com/example/sample",
                ),
            ),
            plan.directoryMoves,
        )
    }

    @Test
    fun usesAValidLowerCamelPrefixAndReviewsWorkedCoreReferences() {
        write(
            "app/src/main/kotlin/com/isaacudy/ukpt/App.kt",
            """
            import feature.ukpt.ukptClientDependencies
            import feature.ukpt.ui.UkptDestination

            val modules = ukptClientDependencies
            val destination = UkptDestination
            val title = "ukpt"
            """.trimIndent(),
        )

        val occurrences = ProjectRenamePlanner.plan(repository, request).occurrences

        assertTrue(occurrences.any {
            it.source == "ukpt" && it.replacement == "SampleApp".replaceFirstChar(Char::lowercaseChar) &&
                it.disposition == RenameDisposition.REVIEW
        })
        assertTrue(occurrences.any {
            it.source == "Ukpt" && it.replacement == "SampleApp" &&
                it.disposition == RenameDisposition.REVIEW
        })
        assertTrue(occurrences.any {
            it.source == "ukpt" && it.replacement == "sample-app" &&
                it.disposition == RenameDisposition.REPLACE
        })
    }

    @Test
    fun rejectsUnsafeIdentityValues() {
        val errors = ProjectRenamePlanner.validate(
            ProjectRenameRequest(
                projectName = "../Sample",
                packageName = "Example",
                typePrefix = "sample-app",
            ),
        )

        assertEquals(3, errors.size)
    }

    private fun write(relativePath: String, contents: String) {
        val file = repository.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(contents)
    }
}
