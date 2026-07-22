package platform.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every image a `design-system/` page links to must exist on disk.
 *
 * Doc surfaces are only worth their cost if the docs and the code cannot drift. A renamed test
 * method renames its golden and silently breaks the embed — the page still renders, just with a
 * hole in it, which nobody notices for months. This turns that into a failing build.
 *
 * It hard-asserts rather than warning: a warning in a test report is the same as no check at all.
 * The one exception is the bootstrap case — before goldens have ever been recorded there is nothing
 * to point at, so a run with no goldens at all is skipped rather than failed.
 */
class DesignSystemDocImagesTest {

    @Test
    fun everyReferencedImageExists() {
        val moduleRoot = findModuleRoot()
        val docsRoot = File(moduleRoot, "design-system")
        assertTrue("expected a design-system/ folder at ${docsRoot.path}", docsRoot.isDirectory)

        val snapshotsRoot = File(moduleRoot, "src/androidHostTest/snapshots/images")
        if (!snapshotsRoot.isDirectory || snapshotsRoot.listFiles().isNullOrEmpty()) {
            // Bootstrap: goldens have never been recorded, so no reference can resolve yet.
            return
        }

        val missing = docsRoot.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .flatMap { page ->
                imageLinks(page.readText()).map { link -> page to link }
            }
            .filterNot { (page, link) -> File(page.parentFile, link).exists() }
            .map { (page, link) -> "${page.relativeTo(moduleRoot).path} → $link" }
            .toList()

        assertTrue(
            "design-system pages reference images that do not exist:\n" +
                missing.joinToString("\n") { "  $it" },
            missing.isEmpty(),
        )
    }

    /** Markdown inline images: `![alt](path)`. Ignores remote URLs, which this cannot verify. */
    private fun imageLinks(markdown: String): Sequence<String> =
        Regex("""!\[[^\]]*]\(([^)\s]+)""")
            .findAll(markdown)
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("http://") || it.startsWith("https://") }

    /**
     * Walks up from the working directory to the module that owns `design-system/`. The working
     * directory of a host test is not contractual, so this does not assume it.
     */
    private fun findModuleRoot(): File {
        var candidate: File? = File(".").absoluteFile.normalize()
        while (candidate != null) {
            if (File(candidate, "design-system").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate a directory containing design-system/ above ${File(".").absolutePath}")
    }
}
