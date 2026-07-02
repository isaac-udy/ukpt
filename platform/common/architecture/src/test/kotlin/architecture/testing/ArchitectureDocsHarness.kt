package architecture.testing

import architecture.docs.renderArchitectureDocs
import architecture.registry.ArchitectureDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Doc↔catalog sync: every generated doc (`README.md` + everything under the docs output dir) must
 * match what the catalog and the example sources produce. Subclass with your definition:
 *
 *     class MyArchitectureDocsTest : ArchitectureDocsHarness(MyArchitecture)
 *
 * The test renders the full set into `build/architecture-docs/` and diffs it against the committed
 * files; setting the definition's regenerate flag (or running its regenerate command) writes them
 * into place.
 */
abstract class ArchitectureDocsHarness(private val definition: ArchitectureDefinition) {

    @Test
    fun architectureDocsAreUpToDate() {
        val config = definition.docs
        val root = moduleRoot(config.module)
        val docs = renderArchitectureDocs(definition, root)

        val staging = File(root, "build/architecture-docs")
        staging.deleteRecursively()
        docs.forEach { doc ->
            File(staging, doc.relativePath).apply { parentFile.mkdirs() }.writeText(doc.content)
        }

        val generated = docs.map { it.relativePath }.toSet()
        val stale = docs.filter { doc ->
            val committed = File(root, doc.relativePath)
            !committed.exists() || committed.readText() != doc.content
        }
        val orphans = File(root, config.outputDir).listFiles().orEmpty()
            .filter { it.isFile && it.extension == "md" && "${config.outputDir}/${it.name}" !in generated }

        if (System.getenv(config.regenerateFlag) == "true") {
            stale.forEach { doc ->
                File(root, doc.relativePath).apply { parentFile.mkdirs() }.writeText(doc.content)
                println("Regenerated ${doc.relativePath}")
            }
            orphans.forEach { orphan ->
                orphan.delete()
                println("Removed orphan ${config.outputDir}/${orphan.name}")
            }
            return
        }

        if (stale.isEmpty() && orphans.isEmpty()) return
        fail(
            buildString {
                appendLine("The architecture docs are stale relative to the catalog + example sources:")
                stale.forEach { appendLine("  - ${it.relativePath} (fresh render: build/architecture-docs/${it.relativePath})") }
                orphans.forEach { appendLine("  - ${config.outputDir}/${it.name} is produced by no source (orphan)") }
                appendLine("Regenerate with: ${config.regenerateCommand}")
            },
        )
    }
}

/** Locate the module at [module] (repo-relative) from the test working directory. */
fun moduleRoot(module: String): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        if (dir.path.replace('\\', '/').endsWith("/$module")) return dir
        File(dir, module).let { if (it.isDirectory) return it }
        dir = dir.parentFile
    }
    error("Could not locate module '$module' from ${File("").absolutePath}")
}
