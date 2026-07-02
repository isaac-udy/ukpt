package architecture.docs

import architecture.registry.RuleGroup
import architecture.registry.Status
import architecture.registry.Tag
import architecture.registry.exhaustiveRule
import architecture.registry.membershipRule
import architecture.registry.prepare

/**
 * The `docs/rule-index.md` page: every construct and rule in the catalog, in document order — per
 * group, each construct (a `construct` classification row whose statement is its AND-composed
 * requirements) with its rules, then the group-level rules, then the layer's exhaustiveness rule;
 * finally the global membership rule.
 */
internal fun renderRuleIndexDoc(groups: List<RuleGroup>): String = buildString {
    appendLine("# Rule index")
    appendLine()
    appendLine(
        "The complete catalog, one row per construct or rule. Ids are object/property paths " +
            "(see the [README](../README.md)). `tested` = executable check · `construct` = " +
            "classification requirements · `unverifiable` = mandatory but review-enforced · " +
            "`guidance` = advisory convention · `codegen` = delegated to code generation.",
    )
    appendLine()
    appendLine(renderRuleIndexTable(groups))
}

internal fun renderRuleIndexTable(groups: List<RuleGroup>): String {
    prepare(groups)
    data class Row(val id: String, val statement: String, val marker: String, val source: String)

    fun sourceOf(owner: Any): String =
        "../src/test/kotlin/${owner.javaClass.packageName.replace('.', '/')}/${owner.javaClass.simpleName}.kt"

    val engineSource = "../src/test/kotlin/architecture/registry/Membership.kt"
    val rows = mutableListOf<Row>()
    groups.forEach { group ->
        group.constructs.forEach { construct ->
            rows += Row(construct.id, construct.requirements.joinToString(" · ") { it.description }, Tag.CONSTRUCT.marker, sourceOf(construct))
            construct.declaredRules.filter { it.status is Status.Active }.forEach {
                rows += Row(it.id, it.title, it.tag.marker, sourceOf(construct))
            }
        }
        group.declaredRules.filter { it.status is Status.Active }.forEach {
            rows += Row(it.id, it.title, it.tag.marker, sourceOf(group))
        }
        if (group.inPackage != null) exhaustiveRule(group).let { rows += Row(it.id, it.title, it.tag.marker, engineSource) }
    }
    membershipRule(groups).let { rows += Row(it.id, it.title, it.tag.marker, engineSource) }
    return buildString {
        appendLine("| Rule | Statement | Enforcement |")
        appendLine("| --- | --- | --- |")
        rows.forEach { (id, statement, marker, source) ->
            appendLine("| `$id` | ${statement.replace("|", "\\|")} | [$marker]($source) |")
        }
    }.trimEnd()
}
