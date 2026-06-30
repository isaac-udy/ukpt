package architecture.registry

/**
 * Renders the canonical rule index — every enforced rule as `id | enforcement | statement`, in
 * document order ([enforcedRules]). This is the machine-checkable contract that the README's
 * `RULE-INDEX` block must match; drift is caught by
 * `RegistryArchitectureTest.ruleIndexMatchesReadme`, which regenerates the block from here. Only
 * active rules appear (retired rules document a removal and are not enforced).
 */
internal fun renderRuleIndex(catalog: List<RuleGroup>): String = buildString {
    appendLine("| Rule | Enforcement | Statement |")
    appendLine("| --- | --- | --- |")
    enforcedRules(catalog)
        .filter { it.status is Status.Active }
        .forEach { rule ->
            // `|` would break the markdown table cell; escape it. Titles are otherwise authored
            // table-safe (code spans around the few that contain `<`/`>`).
            val statement = rule.title.replace("|", "\\|")
            appendLine("| `${rule.id}` | ${rule.tag.marker} | $statement |")
        }
}.trimEnd()
