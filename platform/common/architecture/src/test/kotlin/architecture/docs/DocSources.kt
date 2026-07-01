package architecture.docs

import architecture.registry.RuleGroup
import java.io.File

/**
 * The hand-written narrative sources under `src/test/kotlin/architecture/rules/`. Everything is
 * maintained by hand; only the compiled output under `docs/` (and the README) is generated.
 *
 *  - group fragment `rules/<layer>/<Group>.md` — the layer doc's title + intro narrative (required)
 *  - construct fragment `rules/<layer>/<Group>.<Construct>.md` — one construct's narrative (optional)
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
        val groupFragment: File,
        /** Construct id → its narrative fragment; constructs without one render bare. */
        val constructFragments: Map<String, File>,
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
                    groupFragment = File(packageDir, "${group.id}.md"),
                    constructFragments = group.constructs
                        .map { it.id to File(packageDir, "${it.id}.md") }
                        .filter { (_, file) -> file.exists() }
                        .toMap(),
                )
            }
            val readmeTemplate = File(rulesDir, "UkptArchitecture.md")
            val missing = (layers.map { it.groupFragment } + readmeTemplate).filterNot { it.exists() }
            check(missing.isEmpty()) {
                "Missing architecture doc sources:\n" + missing.joinToString("\n") { " - ${it.relativeTo(moduleRoot).path}" }
            }
            val claimed = (layers.flatMap { it.constructFragments.values + it.groupFragment } + readmeTemplate)
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
                "These sources look like group/construct fragments but match nothing in the catalog " +
                    "(typo, or wrong package directory?):\n" +
                    misnamed.joinToString("\n") { " - ${it.relativeTo(moduleRoot).path}" }
            }
            return DocSources(moduleRoot, layers, standalone, readmeTemplate)
        }
    }
}
