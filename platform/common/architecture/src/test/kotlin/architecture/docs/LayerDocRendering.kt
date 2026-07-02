package architecture.docs

import architecture.registry.RuleContainer
import architecture.registry.RuleGroup
import architecture.registry.describeText
import java.io.File

/**
 * Layer docs are **compiled, not assembled by hand**: every layer doc has the same fixed shape, so
 * nothing is embedded by hand and nothing can be missed.
 *
 *  1. `# <Group Name>` (PascalCase spaced) + the group's `@Describe` text
 *  2. `##### Rules` / `##### Guidance` — the group-level rules and guidance, from the catalog
 *  3. `##### Examples` — the group's `<Group>.examples.md`, when present
 *  4. one `## <Construct Name>` section per construct (catalog order): its `@Describe` text, the
 *     generated Definition/Rules/Guidance blocks, then its `<Group.Construct>.examples.md`
 */
internal fun renderLayerDoc(
    layer: DocSources.LayerSource,
    sourcePath: (File) -> String,
    errors: MutableList<String>,
): String = buildString {
    val group = layer.group
    appendLine("# ${spacedName(group.id)}")
    appendLine()
    description(group, errors)?.let {
        appendLine(it)
        appendLine()
    }
    val rules = groupRules(group)
    val guidance = groupGuidance(group)
    if (rules.isNotEmpty()) {
        appendLine("##### Rules")
        appendLine()
        rules.forEach { append(renderRuleBullet(it)) }
        appendLine()
    }
    if (guidance.isNotEmpty()) {
        appendLine("##### Guidance")
        appendLine()
        guidance.forEach { append(renderRuleBullet(it)) }
        appendLine()
    }
    layer.groupExamples?.let { append(renderExamples(it, sourcePath(it), errors)) }
    group.constructs.forEach { construct ->
        appendLine("---")
        appendLine()
        appendLine("## ${spacedName(construct.id.substringAfterLast('.'))}")
        appendLine()
        description(construct, errors)?.let {
            appendLine(it)
            appendLine()
        }
        append(renderConstructBlock(construct))
        appendLine()
        layer.constructExamples[construct.id]?.let { append(renderExamples(it, sourcePath(it), errors)) }
    }
}.trimEnd() + "\n"

/** "DataLayer" → "Data Layer", "DomainInterface" → "Domain Interface". */
internal fun spacedName(name: String): String = name.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")

/** A group/construct's narrative is its `@Describe` text — required. */
private fun description(container: RuleContainer, errors: MutableList<String>): String? {
    val text = container::class.describeText()
    val group = container as? RuleGroup
    val id = group?.id ?: (container as architecture.registry.Construct).id
    if (text == null) errors += "$id: the object needs a @Describe(\"…\") with its narrative description"
    return text
}

/** An `<Id>.examples.md` file: raw markdown, no markers, rendered under an Examples header. */
private fun renderExamples(file: File, where: String, errors: MutableList<String>): String = buildString {
    val content = file.readText().trim()
    forEachProseLine(content) { line ->
        if (markerLine.matches(line.trim())) {
            errors += "$where: markers are not supported in examples files"
        }
    }
    appendLine("##### Examples")
    appendLine()
    appendLine(content)
    appendLine()
}
