package architecture.docs

import architecture.registry.RuleGroup
import java.io.File

/**
 * The hand-written narrative sources under `src/test/kotlin/architecture/rules/`:
 *
 *  - each group's sidecar, `rules/<layer>/<GroupName>.md`, next to the `.kt` that declares its rules
 *  - the README template, `rules/UkptArchitecture.md`, next to the catalog object
 *  - any other `.md` under `rules/` is a standalone doc (e.g. `exceptions.md`) rendered to `docs/`
 */
internal class DocSources(
    private val moduleRoot: File,
    val groupSidecars: List<Pair<RuleGroup, File>>,
    val standalone: List<File>,
    val readmeTemplate: File,
) {
    /** `architecture.rules.data` → `data.md`: the layer doc is named after its sub-package. */
    fun outputName(group: RuleGroup): String = group.javaClass.packageName.substringAfterLast('.')

    fun sourcePath(file: File): String = file.relativeTo(moduleRoot).path

    companion object {
        fun discover(moduleRoot: File, groups: List<RuleGroup>): DocSources {
            val kotlinRoot = File(moduleRoot, "src/test/kotlin")
            val rulesDir = File(kotlinRoot, "architecture/rules")
            val sidecars = groups.map { group ->
                val packageDir = File(kotlinRoot, group.javaClass.packageName.replace('.', '/'))
                group to File(packageDir, "${group.id}.md")
            }
            val readmeTemplate = File(rulesDir, "UkptArchitecture.md")
            val missing = (sidecars.map { it.second } + readmeTemplate).filterNot { it.exists() }
            check(missing.isEmpty()) {
                "Missing architecture doc sources:\n" + missing.joinToString("\n") { " - ${it.relativeTo(moduleRoot).path}" }
            }
            val claimed = (sidecars.map { it.second } + readmeTemplate).map { it.canonicalFile }.toSet()
            val groupNames = groups.map { "${it.id}.md" }.toSet()
            val standalone = rulesDir.walkTopDown()
                .filter { it.isFile && it.extension == "md" && it.canonicalFile !in claimed }
                .sortedBy { it.name }
                .toList()
            val misplaced = standalone.filter { it.name in groupNames || it.name == "UkptArchitecture.md" }
            check(misplaced.isEmpty()) {
                "These sources shadow a group sidecar but sit in the wrong package directory:\n" +
                    misplaced.joinToString("\n") { " - ${it.relativeTo(moduleRoot).path}" }
            }
            return DocSources(moduleRoot, sidecars, standalone, readmeTemplate)
        }
    }
}
