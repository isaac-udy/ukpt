package architecture.registry

import architecture.ArchitectureExceptions
import architecture.definitions.isFeatureModule
import architecture.definitions.isInsideFunction
import architecture.definitions.isPrivate
import architecture.projectScope
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
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

// ---- exhaustiveness + membership ---------------------------------------------------------------

private fun exhaustiveRule(group: RuleGroup): Rule = Rule(
    id = "${group.id}.exhaustive",
    title = "Every top-level declaration in `${group.inPackage}` matches exactly one construct",
    rationale = """
        A declaration here that matches no construct (or more than one) is either mis-placed or a shape
        the architecture doesn't recognise. Make it conform to a construct, or add one.
    """.trimIndent(),
    enforcement = ScopeConstraint(membershipCheck(group.constructs, group.inPackage)),
    status = Status.Active,
    notes = emptyList(),
)

private fun membershipRule(groups: List<RuleGroup>): Rule = Rule(
    id = "architecture.everyDeclarationBelongsToALayer",
    title = "Every feature-module declaration matches exactly one construct across all layers",
    rationale = """
        A class/interface/object/function/property in a feature module that matches no construct (or
        more than one) is mis-placed or an unrecognised shape. Covers declarations that aren't in any
        single layer package (e.g. a feature's DI module).
    """.trimIndent(),
    enforcement = ScopeConstraint(membershipCheck(groups.flatMap { it.constructs }, pkg = null)),
    status = Status.Active,
    notes = emptyList(),
)

/**
 * Shared classification check: every classifiable declaration (optionally restricted to [pkg], else
 * any feature module) must match exactly one of [constructs]; partial matches get a rich breakdown.
 */
private fun membershipCheck(constructs: List<Construct>, pkg: String?): ScopeCheck =
    ScopeCheck { scope, exempt ->
        classifiableDeclarations(scope)
            .filter { if (pkg != null) it.residesIn(pkg) else it.isFeatureModule() }
            .filterNot { exempt(it) || ArchitectureExceptions.isIgnored(it) }
            .filter { decl -> constructs.count { it.test(decl) } != 1 }
            .map { Violation(it, classifyMessage(it, constructs)) }
    }

private fun classifiableDeclarations(scope: KoScope): List<KoBaseDeclaration> =
    scope.declarations(includeNested = false)
        .filter {
            it is KoClassDeclaration || it is KoInterfaceDeclaration || it is KoObjectDeclaration ||
                it is KoFunctionDeclaration || it is KoPropertyDeclaration
        }
        .filterNot { it.isPrivate() }
        .filterNot { it.isInsideFunction() }

/** Human breakdown for a declaration that matched no construct (or several). */
private fun classifyMessage(declaration: KoBaseDeclaration, constructs: List<Construct>): String {
    val location = declaration.sourceLocation()
    val matched = constructs.filter { it.test(declaration) }
    return when {
        matched.size > 1 -> "$location matches multiple constructs: ${matched.joinToString { it.id }}"
        else -> buildString {
            val scored = constructs
                .map { c -> c to c.requirements.count { it.matches(declaration) }.toDouble() / c.requirements.size }
                .filter { it.second > 0.0 }
            val best = scored.maxOfOrNull { it.second } ?: 0.0
            val closest = scored.filter { best - it.second < 0.15 }.sortedByDescending { it.second }
            append("$location matches no construct")
            if (closest.isNotEmpty()) {
                appendLine("; closest:")
                closest.forEach { (construct, pct) ->
                    appendLine("    ${construct.id} (${(pct * 100).toInt()}%)")
                    construct.requirements.forEach { req ->
                        appendLine("        [${if (req.matches(declaration)) "✓" else " "}] ${req.description}")
                    }
                }
            }
        }
    }
}

// ---- integrity + report ------------------------------------------------------------------------

private fun integrityChecks(rules: List<Rule>) {
    val duplicates = rules.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isNotEmpty()) fail("Duplicate rule ids in the catalog: ${duplicates.sorted()}")
    val known = rules.map { it.id }.toSet()
    val dangling = rules.mapNotNull { rule ->
        (rule.enforcement as? architecture.registry.DelegatedConstraint)?.by
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

// ---- README rule index -------------------------------------------------------------------------

/**
 * The canonical catalog index, in document order: per group, each construct (a `🔶 construct`
 * classification row whose statement is its AND-composed requirements) with its functionality rules,
 * then the group-level rules, then the layer's exhaustiveness rule; finally the membership rule.
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
