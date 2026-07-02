package architecture.docs

import architecture.registry.ArchitectureDefinition
import architecture.registry.RuleGroup
import java.io.File

/**
 * The hand-written sources under the definition's catalog packages. Narrative lives in `@Describe`
 * annotations in the catalog itself; markdown files carry only what markdown is best at:
 *
 *  - `<catalog>/<layer>/<Group>.examples.md` — the layer's example blocks (optional)
 *  - `<catalog>/<layer>/<Construct>.examples.md` — one construct's examples, beside `<Construct>.kt` (optional)
 *  - any other `.md` directly in the catalog root is a standalone doc (e.g. `exceptions.md`)
 *
 * (The README template is not a file — it is the `@Describe` on the definition object.)
 */
internal class DocSources(
    private val moduleRoot: File,
    private val definition: ArchitectureDefinition,
    val layers: List<LayerSource>,
    val standalone: List<File>,
) {
    class LayerSource(
        val group: RuleGroup,
        val groupExamples: File?,
        /** Construct id → its narrative fragment; constructs without one render bare. */
        val constructExamples: Map<String, File>,
    )

    /** `…rules.data` → `data.md`: the layer doc is named after its sub-package. */
    fun outputName(group: RuleGroup): String = group.javaClass.packageName.substringAfterLast('.')

    fun sourcePath(file: File): String = file.relativeTo(moduleRoot).path

    fun packageDirPath(group: RuleGroup): String =
        "${definition.docs.sourceRoot}/${group.javaClass.packageName.replace('.', '/')}"

    companion object {
        fun discover(moduleRoot: File, definition: ArchitectureDefinition): DocSources {
            val groups = definition.groups
            val kotlinRoot = File(moduleRoot, definition.docs.sourceRoot)
            val catalogRoot = File(kotlinRoot, definition.javaClass.packageName.replace('.', '/'))
            val layers = groups.map { group ->
                val packageDir = File(kotlinRoot, group.javaClass.packageName.replace('.', '/'))
                LayerSource(
                    group = group,
                    groupExamples = File(packageDir, "${group.id}.examples.md").takeIf { it.exists() },
                    constructExamples = group.constructs
                        .map { it.id to File(packageDir, "${it.javaClass.simpleName}.examples.md") }
                        .filter { (_, file) -> file.exists() }
                        .toMap(),
                )
            }
            val claimed = layers.flatMap { it.constructExamples.values + listOfNotNull(it.groupExamples) }
                .map { it.canonicalFile }
                .toSet()
            val groupIds = groups.map { it.id }.toSet()
            val (misnamed, standalone) = catalogRoot.walkTopDown()
                .filter { it.isFile && it.extension == "md" && it.canonicalFile !in claimed }
                .sortedBy { it.name }
                .partition { file ->
                    file.parentFile.canonicalFile != catalogRoot.canonicalFile ||
                        file.name.endsWith(".examples.md") ||
                        file.name == "${definition.name}.md" ||
                        file.name.substringBefore('.') in groupIds
                }
            check(misnamed.isEmpty()) {
                "These sources match nothing in the catalog (a `<Construct>.examples.md` typo, the wrong " +
                    "package directory, or a leftover narrative fragment?):\n" +
                    misnamed.joinToString("\n") { " - ${it.relativeTo(moduleRoot).path}" }
            }
            return DocSources(moduleRoot, definition, layers, standalone)
        }
    }
}
