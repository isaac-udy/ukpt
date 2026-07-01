package architecture.docs

import architecture.registry.Construct
import architecture.registry.DelegatedConstraint
import architecture.registry.NotEnforced
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
                else -> out.append(renderRuleBullet(rule))
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

/**
 * A construct section's generated tail: the **Definition** (classification requirements), then the
 * enforced **Rules**, then the advisory **Guidance** — each block only when non-empty.
 */
internal fun renderConstructBlock(construct: Construct): String = buildString {
    appendLine("**Definition** — a declaration is a `${construct.id}` when it satisfies all of:")
    appendLine()
    construct.requirements.forEach { appendLine("* ${it.description}") }
    val (guidance, rules) = construct.declaredRules
        .filter { it.status is Status.Active }
        .partition { it.tag == Tag.GUIDANCE }
    if (rules.isNotEmpty()) {
        appendLine()
        appendLine("**Rules**:")
        appendLine()
        rules.forEach { append(renderRuleBullet(it)) }
    }
    if (guidance.isNotEmpty()) {
        appendLine()
        appendLine("**Guidance**:")
        appendLine()
        guidance.forEach { append(renderRuleBullet(it)) }
    }
}

internal fun groupRules(group: RuleGroup): List<Rule> =
    group.declaredRules.filter { it.status is Status.Active && it.tag != Tag.GUIDANCE }

internal fun groupGuidance(group: RuleGroup): List<Rule> =
    group.declaredRules.filter { it.status is Status.Active && it.tag == Tag.GUIDANCE }

/** For the `{{rules:Group}}` marker: the enforced rules, then the guidance, with labels. */
internal fun renderGroupRules(group: RuleGroup): String = buildString {
    val rules = groupRules(group)
    val guidance = groupGuidance(group)
    if (rules.isNotEmpty()) {
        appendLine("**Rules**:")
        appendLine()
        rules.forEach { append(renderRuleBullet(it)) }
    }
    if (guidance.isNotEmpty()) {
        if (rules.isNotEmpty()) appendLine()
        appendLine("**Guidance**:")
        appendLine()
        guidance.forEach { append(renderRuleBullet(it)) }
    }
}

/** Statement first; the id and any context sit underneath, so the rule itself is what you read. */
internal fun renderRuleBullet(rule: Rule): String = buildString {
    appendLine("* ${rule.title}")
    appendLine("    * **ID**: `${rule.id}`")
    if (rule.rationale.isNotBlank()) appendLine("    * **Why**: ${collapse(rule.rationale)}")
    rule.notes.forEach { appendLine("    * **Note**: ${collapse(it)}") }
    when (val enforcement = rule.enforcement) {
        is DelegatedConstraint -> appendLine("    * **Enforced by**: ${enforcement.by.joinToString(", ") { "`$it`" }}")
        is NotEnforced -> if (enforcement.tag == Tag.CODEGEN) {
            appendLine("    * **Enforced by**: the `dev.isaacudy.udytils.postgres` code generator")
        }
        else -> {}
    }
}

/** Rationales/notes are authored as trimIndent paragraphs; bullets need them on one line. */
private fun collapse(text: String): String = text.trim().lines().joinToString(" ") { it.trim() }
