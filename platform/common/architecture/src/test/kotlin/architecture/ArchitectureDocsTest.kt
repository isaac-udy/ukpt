package architecture

import architecture.docs.renderArchitectureDocs
import architecture.rules.UkptArchitecture
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Doc↔catalog sync: every generated doc (`README.md` + everything under `docs/`) must match what the catalog and
 * the sidecar sources produce. The test renders the full set into `build/architecture-docs/` and
 * diffs it against the committed files, so a rule change, a sidecar edit, or a hand-edit to a
 * generated file all surface as one failure. Regenerate with:
 *
 *     UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test
 */
class ArchitectureDocsTest {

    @Test
    fun architectureDocsAreUpToDate() {
        val root = moduleRoot()
        val docs = renderArchitectureDocs(UkptArchitecture.all, root, UkptArchitecture.readme)

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
        val orphans = File(root, "docs").listFiles().orEmpty()
            .filter { it.isFile && it.extension == "md" && "docs/${it.name}" !in generated }

        if (System.getenv("UPDATE_ARCHITECTURE_DOCS") == "true") {
            stale.forEach { doc ->
                File(root, doc.relativePath).apply { parentFile.mkdirs() }.writeText(doc.content)
                println("Regenerated ${doc.relativePath}")
            }
            orphans.forEach { orphan ->
                orphan.delete()
                println("Removed orphan docs/${orphan.name}")
            }
            return
        }

        if (stale.isEmpty() && orphans.isEmpty()) return
        fail(
            buildString {
                appendLine("The architecture docs are stale relative to the catalog + sidecar sources:")
                stale.forEach { appendLine("  - ${it.relativePath} (fresh render: build/architecture-docs/${it.relativePath})") }
                orphans.forEach { appendLine("  - docs/${it.name} is produced by no source (orphan)") }
                appendLine("Regenerate with: UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test")
            },
        )
    }
}

/** Locate the architecture module root from the test working directory. */
internal fun moduleRoot(): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        if (File(dir, "src/test/kotlin/architecture/registry").exists()) return dir
        File(dir, "platform/common/architecture").let { candidate ->
            if (File(candidate, "src/test/kotlin/architecture/registry").exists()) return candidate
        }
        dir = dir.parentFile
    }
    error("Could not locate the architecture module root from ${File("").absolutePath}")
}
