package architecture.docs

import architecture.registry.RuleGroup
import java.io.File

/**
 * The hand-written sources under `src/test/kotlin/architecture/rules/`. Narrative lives in
 * `@Describe` annotations in the catalog itself; markdown files carry only what markdown is best at:
 *
 *  - `rules/<layer>/<Group>.examples.md` — the layer's example blocks (optional)
 *  - `rules/<layer>/<Group>.<Construct>.examples.md` — one construct's examples (optional)
 *  - README template `rules/UkptArchitecture.md` — next to the catalog object
 *  - any other `.md` under `rules/` is a standalone doc (e.g. `exceptions.md`) rendered to `docs/`
 */
internal class DocSources(
    private val moduleRoot: File,
    val layers: List<LayerSource>,
    val standalone: List<File>,
    val readmeTemplate: File,
) {
    class LayerSource(
        val group: RuleGroup,
        val groupExamples: File?,
        /** Construct id → its examples file; constructs without one render without examples. */
        val constructExamples: Map<String, File>,
    )

    /** `architecture.rules.data` → `data.md`: the layer doc is named after its sub-package. */
    fun outputName(group: RuleGroup): String = group.javaClass.packageName.substringAfterLast('.')

    fun sourcePath(file: File): String = file.relativeTo(moduleRoot).path

    fun packageDirPath(group: RuleGroup): String =
        "src/test/kotlin/" + group.javaClass.packageName.replace('.', '/')

    companion object {
        fun discover(moduleRoot: File, groups: List<RuleGroup>): DocSources {
            val kotlinRoot = File(moduleRoot, "src/test/kotlin")
            val rulesDir = File(kotlinRoot, "architecture/rules")
            val layers = groups.map { group ->
                val packageDir = File(kotlinRoot, group.javaClass.packageName.replace('.', '/'))
                LayerSource(
                    group = group,
                    groupExamples = File(packageDir, "${group.id}.examples.md").takeIf { it.exists() },
                    constructExamples = group.constructs
                        .map { it.id to File(packageDir, "${it.id}.examples.md") }
                        .filter { (_, file) -> file.exists() }
                        .toMap(),
                )
            }
            val readmeTemplate = File(rulesDir, "UkptArchitecture.md")
            check(readmeTemplate.exists()) { "Missing README template: ${readmeTemplate.relativeTo(moduleRoot).path}" }
            val claimed = (layers.flatMap { it.constructExamples.values + listOfNotNull(it.groupExamples) } + readmeTemplate)
                .map { it.canonicalFile }
                .toSet()
            val groupIds = groups.map { it.id }.toSet()
            val (misnamed, standalone) = rulesDir.walkTopDown()
                .filter { it.isFile && it.extension == "md" && it.canonicalFile !in claimed }
                .sortedBy { it.name }
                .partition { file ->
                    file.name == "UkptArchitecture.md" ||
                        file.name.substringBefore('.') in groupIds
                }
            check(misnamed.isEmpty()) {
                "These sources look like `<Id>.examples.md` files but match nothing in the catalog " +
                    "(typo, wrong package directory, or a leftover narrative fragment?):\n" +
                    misnamed.joinToString("\n") { " - ${it.relativeTo(moduleRoot).path}" }
            }
            return DocSources(moduleRoot, layers, standalone, readmeTemplate)
        }
    }
}
