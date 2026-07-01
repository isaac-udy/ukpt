package architecture.registry

import architecture.projectScope
import com.lemonappdev.konsist.api.container.KoScope
import kotlin.test.fail

/**
 * The single architecture entry point for the object registry. Builds each input once (the Konsist
 * scope + parsed module graph), runs every active rule, and fails once with a grouped report.
 */
fun verify(groups: List<RuleGroup>, exclude: Set<String> = emptySet()) {
    val rules = enforcedRules(groups)
    integrityChecks(rules)
    val ctx = RunContext(projectScope, ModuleGraph.parse())
    val findings = rules
        .filter { it.status is Status.Active && it.id !in exclude }
        .flatMap { rule -> rule.run(ctx) }
    if (findings.isNotEmpty()) fail(render(findings))
}

/**
 * Every rule the catalog enforces, in document order: per group the group-level rules, then each
 * construct's rules, then the layer's exhaustiveness rule; finally the global membership rule. The
 * single source for both [verify] and [renderRuleIndex].
 */
internal fun enforcedRules(groups: List<RuleGroup>): List<Rule> {
    prepare(groups)
    val rules = mutableListOf<Rule>()
    groups.forEach { group ->
        rules += group.declaredRules
        group.constructs.forEach { rules += it.declaredRules }
        if (group.inPackage != null) rules += exhaustiveRule(group)
    }
    rules += membershipRule(groups)
    return rules
}

private class RunContext(val scope: KoScope, val graph: ModuleGraph)

private data class Finding(val rule: Rule, val where: String, val message: String)

private fun Rule.run(ctx: RunContext): List<Finding> = when (val e = enforcement) {
    is ScopeConstraint ->
        e.check.run(ctx.scope) { decl -> Exemptions.isExempt(id, decl) }.map { Finding(this, it.where, it.message) }
    is ModuleGraphConstraint ->
        e.check.run(ctx.graph) { edge -> Exemptions.isExempt(id, edge) }.map { Finding(this, it.where, it.message) }
    else -> emptyList() // DelegatedConstraint (enforced transitively) / NotEnforced (guidance / codegen)
}

private fun integrityChecks(rules: List<Rule>) {
    val duplicates = rules.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isNotEmpty()) fail("Duplicate rule ids in the catalog: ${duplicates.sorted()}")
    val known = rules.map { it.id }.toSet()
    val dangling = rules.mapNotNull { rule ->
        (rule.enforcement as? DelegatedConstraint)?.by
            ?.filterNot { it in known }?.takeIf { it.isNotEmpty() }?.let { rule.id to it }
    }
    if (dangling.isNotEmpty()) fail("enforcedBy(...) references unknown rule ids: $dangling")
}

private fun render(findings: List<Finding>): String = buildString {
    appendLine("Architecture verification failed — ${findings.size} violation(s):")
    findings.groupBy { it.rule }.toList().sortedBy { (rule, _) -> rule.id }.forEach { (rule, group) ->
        appendLine()
        appendLine("[${rule.id}] ${rule.title}")
        if (rule.rationale.isNotBlank()) appendLine(rule.rationale.trim().prependIndent("    "))
        group.forEach { appendLine("  - ${it.where}: ${it.message}") }
    }
}
