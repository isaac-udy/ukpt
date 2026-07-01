package architecture.registry

/**
 * The canonical catalog index, in document order: per group, each construct (a `🔶 construct`
 * classification row whose statement is its AND-composed requirements) with its functionality rules,
 * then the group-level rules, then the layer's exhaustiveness rule; finally the membership rule.
 * The README's `RULE-INDEX` block is regenerated from this (guarded by `ruleIndexMatchesReadme`).
 */
internal fun renderRuleIndex(groups: List<RuleGroup>): String {
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
