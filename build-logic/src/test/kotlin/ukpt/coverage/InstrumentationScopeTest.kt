package ukpt.coverage

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InstrumentationScopeTest {
    @TempDir
    lateinit var root: File

    private val project by lazy { ProjectBuilder.builder().withProjectDir(root).build() }
    private val extension by lazy {
        project.extensions.create("testCoverage", TestCoverageExtension::class.java).apply {
            instrumentedPackages("feature.*", "platform.*", "app.*", "com.example.*")
        }
    }

    private fun report(): TestCoverageReportTask {
        val manifest = CoverageManifest(
            emptyList(), listOf(":platform:x" to "platform/x"), mapOf("jvm" to listOf("platform/x/shared")), emptyList(),
            extension.instrumentedPackages.get(), setOf("feature.*.tests.*", "enro_generated_bindings.*", "*.generated.resources.*"),
        )
        val manifestFile = root.resolve("manifest.json").also { manifest.write(it, root) }
        val classificationFile = root.resolve("classification.json")
        classificationFile.writeText("""
            {"categories":[],"constructs":[],"declarations":[
              {"path":"platform/x/shared/Tool.kt","name":"ukpt.tools.Tool","codeScope":"production","startLine":1,"endLine":2},
              {"path":"platform/x/src/main/Hammer.kt","name":"ukpt.tools.hand.Hammer","codeScope":"production","startLine":1,"endLine":2},
              {"path":"platform/x/src/main/Config.kt","name":"com.example.deep.Config","codeScope":"production","startLine":1,"endLine":2},
              {"path":"platform/x/src/test/Spec.kt","name":"other.Spec","codeScope":"test","startLine":1,"endLine":2},
              {"path":"platform/xy/src/main/Outside.kt","name":"other.Outside","codeScope":"production","startLine":1,"endLine":2},
              {"path":"platform/x/src/main/Res.kt","name":"other.generated.resources.Res","codeScope":"production","startLine":1,"endLine":2},
              {"path":"platform/x/src/main/Bindings.kt","name":"enro_generated_bindings.Bindings","codeScope":"production","startLine":1,"endLine":2}
            ]}
        """.trimIndent())
        return project.tasks.register("testCoverageReport", TestCoverageReportTask::class.java).get().apply {
            repositoryDirectory.set(root)
            outputDirectory.set(root.resolve("report"))
            this.manifest.set(manifestFile)
            classification.set(classificationFile)
        }
    }

    @Test
    fun reportFailsWithTheMissingPackageAndItsSourceEvenIfConfigurationHasSinceChanged() {
        val report = report()
        extension.instrumentedPackages("ukpt.tools.*")
        val failure = assertFailsWith<IllegalStateException> { report.render() }
        assertContains(failure.message.orEmpty(), "platform/x/shared/Tool.kt")
        assertContains(failure.message.orEmpty(), """instrumentedPackages("ukpt.tools.*")""")
        assertEquals(1, failure.message.orEmpty().lines().size)
    }

    @Test
    fun addingThePackageBeforeCollectionLetsTheReportRender() {
        extension.instrumentedPackages("ukpt.tools.*")
        report().render()
        assertTrue(root.resolve("report/coverage.json").isFile)
    }

    @Test
    fun aPackagePatternRequiresAPackageAndATrailingWildcard() {
        for (pattern in listOf("*", ".*", "ukpt.tools")) {
            assertFailsWith<IllegalArgumentException> { extension.instrumentedPackages(pattern) }
        }
    }
}
