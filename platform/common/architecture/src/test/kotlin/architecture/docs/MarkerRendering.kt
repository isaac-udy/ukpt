package architecture.docs

import architecture.registry.Construct
import architecture.registry.DelegatedConstraint
import architecture.registry.Rule
import architecture.registry.RuleGroup
import architecture.registry.Status
import architecture.registry.Tag

/**
 * Marker expansion for **standalone docs and the README template** (layer docs are compiled with a
 * fixed structure instead — see [renderLayerDoc]). A marker sits alone on a line, outside code fences:
 *
 *  - `{{rule:Full.Rule.Id}}` — one rule bullet (id, tag, statement, rationale/notes)
 *  - `{{rules:Group}}` — every group-level rule as a bullet
 *  - `{{construct:Group.Construct}}` — a construct's classification requirements + its rules
 *  - `{{toc}}` — links to every generated doc (README template only)
 */
internal val markerLine = Regex("""^\{\{([a-z]+)(?::([A-Za-z0-9_.]+))?}}$""")

internal fun expandMarkers(
    source: String,
    catalog: CatalogIndex,
    where: String,
    errors: MutableList<String>,
    toc: List<Pair<String, String>>? = null,
): String {
    val out = StringBuilder()
    var inFence = false
    source.lineSequence().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
            out.appendLine(line)
            return@forEach
        }
        val marker = if (inFence) null else markerLine.matchEntire(line.trim())
        if (marker == null) {
            out.appendLine(line)
            return@forEach
        }
        val kind = marker.groupValues[1]
        val arg = marker.groupValues[2]
        when (kind) {
            "construct" -> when (val construct = catalog.constructsById[arg]) {
                null -> errors += "$where: {{construct:$arg}} does not match any construct in the catalog"
                else -> out.append(renderConstructBlock(construct))
            }
            "rules" -> when (val group = catalog.groupsById[arg]) {
                null -> errors += "$where: {{rules:$arg}} does not match any group in the catalog"
                else -> out.append(renderGroupRules(group))
            }
            "rule" -> when (val rule = catalog.rulesById[arg]) {
                null -> errors += "$where: {{rule:$arg}} does not match any rule in the catalog"
                else -> out.append(renderRuleBullet(rule, indent = ""))
            }
            "toc" -> when (toc) {
                null -> errors += "$where: {{toc}} is only supported in the README template"
                else -> toc.forEach { (path, title) -> out.appendLine("- [$title]($path)") }
            }
            else -> errors += "$where: unknown marker {{$kind}}"
        }
    }
    return out.toString()
}

internal fun renderConstructBlock(construct: Construct): String = buildString {
    appendLine("* **Construct** `${construct.id}` (`${Tag.CONSTRUCT.marker}`) — a declaration is this construct when it satisfies all of:")
    construct.requirements.forEach { appendLine("    * ${it.description}") }
    val rules = construct.declaredRules.filter { it.status is Status.Active }
    if (rules.isNotEmpty()) {
        appendLine("* **Rules**:")
        rules.forEach { append(renderRuleBullet(it, indent = "    ")) }
    }
}

internal fun renderGroupRules(group: RuleGroup): String = buildString {
    group.declaredRules.filter { it.status is Status.Active }.forEach { append(renderRuleBullet(it, indent = "")) }
}

private fun renderRuleBullet(rule: Rule, indent: String): String = buildString {
    appendLine("$indent* **`${rule.id}`** `${rule.tag.marker}` — ${rule.title}")
    if (rule.rationale.isNotBlank()) appendLine("$indent    * **Why**: ${collapse(rule.rationale)}")
    rule.notes.forEach { appendLine("$indent    * **Note**: ${collapse(it)}") }
    (rule.enforcement as? DelegatedConstraint)?.let { delegated ->
        appendLine("$indent    * **Enforced by**: ${delegated.by.joinToString(", ") { "`$it`" }}")
    }
}

/** Rationales/notes are authored as trimIndent paragraphs; bullets need them on one line. */
private fun collapse(text: String): String = text.trim().lines().joinToString(" ") { it.trim() }
