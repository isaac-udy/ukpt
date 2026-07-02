package architecture.registry

import architecture.projectScope
import com.lemonappdev.konsist.api.container.KoScope
import kotlin.test.fail

/**
 * One architecture run over a catalog: builds the Konsist scope + module graph **once** and evaluates
 * rules on demand. Lets the aggregate [verify] and the per-rule `@TestFactory` share a single scan.
 */
class ArchitectureRun(catalog: List<RuleGroup>) {
    /** Every enforced rule, in document order (see [enforcedRules]). */
    val rules: List<Rule> = enforcedRules(catalog)

    private val scope: KoScope by lazy { projectScope }
    private val graph: ModuleGraph by lazy { ModuleGraph.parse() }

    init {
        integrityChecks(rules)
    }

    /** Violations for one rule (empty = passes). Delegated / guidance / codegen rules run nothing. */
    fun violations(rule: Rule): List<Violation> = when (val e = rule.enforcement) {
        is ScopeConstraint -> e.check.run(scope) { decl -> Exemptions.isExempt(rule.id, decl) }
        is ModuleGraphConstraint -> e.check.run(graph) { edge -> Exemptions.isExempt(rule.id, edge) }
        else -> emptyList()
    }

    /** Findings for a guidance audit — reported, never failing. Empty for rules without an audit. */
    fun auditFindings(rule: Rule): List<Violation> = when (val audit = (rule.enforcement as? NotEnforced)?.audit) {
        is ScopeConstraint -> audit.check.run(scope) { decl -> Exemptions.isExempt(rule.id, decl) }
        is ModuleGraphConstraint -> audit.check.run(graph) { edge -> Exemptions.isExempt(rule.id, edge) }
        else -> emptyList()
    }
}

/**
 * The single aggregate entry point: runs every active rule and fails once with a grouped report.
 * [exclude] drops rules by id (e.g. a downstream overlay).
 */
fun verify(groups: List<RuleGroup>, exclude: Set<String> = emptySet()) {
    val run = ArchitectureRun(groups)
    val findings = run.rules
        .filter { it.status is Status.Active && it.id !in exclude }
        .flatMap { rule -> run.violations(rule).map { Finding(rule, it.where, it.message) } }
    if (findings.isNotEmpty()) fail(render(findings))
}

/**
 * Every rule the catalog enforces, in document order: per group the group-level rules, then each
 * construct's rules, then the layer's exhaustiveness rule; finally the global membership rule. The
 * single source for both [verify]/[ArchitectureRun] and [renderRuleIndex].
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

private data class Finding(val rule: Rule, val where: String, val message: String)

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
