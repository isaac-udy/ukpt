package architecture.docs

import architecture.registry.RuleGroup
import architecture.registry.Status
import architecture.registry.Tag
import architecture.registry.exhaustiveRule
import architecture.registry.membershipRule
import architecture.registry.prepare

/**
 * The `docs/rule-index.md` page: every construct and rule in the catalog, in document order — per
 * group, each construct (a `🔶 construct` classification row whose statement is its AND-composed
 * requirements) with its rules, then the group-level rules, then the layer's exhaustiveness rule;
 * finally the global membership rule.
 */
internal fun renderRuleIndexDoc(groups: List<RuleGroup>): String = buildString {
    appendLine("# Rule index")
    appendLine()
    appendLine(
        "The complete catalog, one row per construct or rule. Ids are object/property paths " +
            "(see the [README](../README.md)). `✅ tested` = executable check · `🔶 construct` = " +
            "classification requirements · `📋 guidance` = documented convention · `⚙️ codegen` = " +
            "delegated to code generation.",
    )
    appendLine()
    appendLine(renderRuleIndexTable(groups))
}

internal fun renderRuleIndexTable(groups: List<RuleGroup>): String {
    prepare(groups)
    val rows = mutableListOf<Triple<String, String, String>>()
    fun add(id: String, marker: String, statement: String) = rows.add(Triple(id, marker, statement))
    groups.forEach { group ->
        group.constructs.forEach { construct ->
            add(construct.id, Tag.CONSTRUCT.marker, construct.requirements.joinToString(" · ") { it.description })
            construct.declaredRules.filter { it.status is Status.Active }.forEach { add(it.id, it.tag.marker, it.title) }
        }
        group.declaredRules.filter { it.status is Status.Active }.forEach { add(it.id, it.tag.marker, it.title) }
        if (group.inPackage != null) exhaustiveRule(group).let { add(it.id, it.tag.marker, it.title) }
    }
    membershipRule(groups).let { add(it.id, it.tag.marker, it.title) }
    return buildString {
        appendLine("| Rule | Enforcement | Statement |")
        appendLine("| --- | --- | --- |")
        rows.forEach { (id, marker, statement) -> appendLine("| `$id` | $marker | ${statement.replace("|", "\\|")} |") }
    }.trimEnd()
}
