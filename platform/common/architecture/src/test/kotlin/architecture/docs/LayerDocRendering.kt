package architecture.docs

import architecture.registry.Status
import java.io.File

/**
 * Layer docs are **compiled, not assembled by hand**: every layer doc has the same fixed shape, so
 * fragment authors never decide where generated content is embedded.
 *
 *  1. `# <title>` + narrative from the group fragment (`rules/<layer>/<Group>.md`)
 *  2. `## Rules` / `## Guidance` — the group-level rules and guidance, generated from the catalog
 *  3. one `## <title>` section per construct (catalog order): narrative from the construct fragment
 *     (`rules/<layer>/<Group>.<Construct>.md`, optional), then the generated
 *     Definition/Rules/Guidance blocks
 */
internal fun renderLayerDoc(
    layer: DocSources.LayerSource,
    sourcePath: (File) -> String,
    errors: MutableList<String>,
): String = buildString {
    val group = layer.group
    parseFragment(layer.groupFragment, sourcePath(layer.groupFragment), errors)?.let { fragment ->
        appendLine("# ${fragment.title}")
        appendLine()
        if (fragment.body.isNotBlank()) {
            appendLine(fragment.body)
            appendLine()
        }
    }
    val rules = groupRules(group)
    val guidance = groupGuidance(group)
    if (rules.isNotEmpty()) {
        appendLine("## Rules")
        appendLine()
        rules.forEach { append(renderRuleBullet(it)) }
        appendLine()
    }
    if (guidance.isNotEmpty()) {
        appendLine("## Guidance")
        appendLine()
        guidance.forEach { append(renderRuleBullet(it)) }
        appendLine()
    }
    group.constructs.forEach { construct ->
        val fragment = layer.constructFragments[construct.id]
            ?.let { parseFragment(it, sourcePath(it), errors) }
        appendLine("## ${fragment?.title ?: construct.id.substringAfterLast('.')}")
        appendLine()
        if (fragment != null && fragment.body.isNotBlank()) {
            appendLine(fragment.body)
            appendLine()
        }
        append(renderConstructBlock(construct))
        appendLine()
    }
}.trimEnd() + "\n"

private class Fragment(val title: String, val body: String)

/** A fragment is plain narrative: it must open with a `# Title` heading and may not use markers. */
private fun parseFragment(file: File, where: String, errors: MutableList<String>): Fragment? {
    val lines = file.readText().lines()
    forEachProseLine(lines.joinToString("\n")) { line ->
        if (markerLine.matches(line.trim())) {
            errors += "$where: markers are not supported in layer fragments — layer docs are compiled automatically"
        }
    }
    val heading = lines.firstOrNull { it.isNotBlank() }
    if (heading == null || !heading.startsWith("# ")) {
        errors += "$where: a fragment must open with a `# Title` heading"
        return null
    }
    val body = lines.drop(lines.indexOf(heading) + 1).joinToString("\n").trim()
    return Fragment(heading.removePrefix("# ").trim(), body)
}
