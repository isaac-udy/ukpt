package architecture.registry

import architecture.ArchitectureExceptions
import architecture.definitions.ConstructDefinition
import architecture.definitions.containingFilePackage
import architecture.definitions.isFeatureModule
import architecture.definitions.isInsideFunction
import architecture.definitions.isPrivate
import architecture.projectScope
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.core.util.LocationUtil
import kotlin.test.fail

/**
 * The single architecture entry point. Builds each input once (the Konsist scope + the parsed
 * module graph), runs every active rule's enforcement, aggregates all violations keyed by rule id,
 * and fails once with a grouped report. [exclude] drops rules by id (e.g. a downstream overlay).
 */
fun verify(catalog: List<RuleGroup>, exclude: Set<String> = emptySet()) {
    integrityChecks(catalog)
    val ctx = RunContext(projectScope, ModuleGraph.parse())

    // Every catalog rule, plus the cross-layer membership rule (every feature declaration must
    // belong to exactly one construct across ALL layers — catches orphans not in any layer package).
    val findings = (catalog.flatMap { it.rules } + membershipRule(catalog))
        .filter { it.status is Status.Active && it.id !in exclude }
        .flatMap { rule -> rule.run(ctx) }

    if (findings.isNotEmpty()) fail(render(findings))
}

private class RunContext(val scope: KoScope, val graph: ModuleGraph)

private data class Finding(val rule: Rule, val where: String, val message: String)

private fun Rule.run(ctx: RunContext): List<Finding> = when (val e = enforcement) {
    is NotEnforced -> emptyList()
    is DelegatedConstraint -> emptyList() // enforced transitively by the rules it names
    is ShapeRequirement -> emptyList() // a classification bit, consumed only by exhaustiveness
    is ScopeConstraint ->
        e.check.run(ctx.scope) { decl -> Exemptions.isExempt(id, decl) }
            .map { Finding(this, it.where, it.message) }
    is ConstructConstraint ->
        ctx.scope.declarations(includeNested = false)
            .filter { e.construct.test(it) }
            .filterNot { Exemptions.isExempt(id, it) }
            .flatMap { decl -> e.check.run(decl) { d -> Exemptions.isExempt(id, d) } }
            .map { Finding(this, it.where, it.message) }
    is ModuleGraphConstraint ->
        e.check.run(ctx.graph) { edge -> Exemptions.isExempt(id, edge) }
            .map { Finding(this, it.where, it.message) }
}

/**
 * The cross-layer membership rule — every feature-module top-level declaration must match exactly
 * one construct across ALL layers' constructs. Reproduces the original
 * `validateAllDeclarationsBelongToDefinedLayer`, which (unlike a per-layer `<layer>.exhaustive`)
 * also covers declarations that aren't in any single layer's package — e.g. a feature's DI module.
 */
private fun membershipRule(catalog: List<RuleGroup>): Rule = Rule(
    id = "architecture.everyDeclarationBelongsToALayer",
    title = "Every feature-module declaration matches exactly one construct across all layers",
    rationale = """
        A class/interface/object/function/property in a feature module that matches no construct
        (or more than one) is mis-placed or an unrecognised shape.
    """.trimIndent(),
    enforcement = ScopeConstraint(membershipCheck(catalog.flatMap { it.constructs })),
    status = Status.Active,
    notes = emptyList(),
)

private fun membershipCheck(allConstructs: List<Construct>): ScopeCheck =
    ScopeCheck { scope, exempt ->
        scope.declarations(includeNested = false)
            .filterNot { it is KoFileDeclaration }
            .filter {
                it is KoClassDeclaration || it is KoInterfaceDeclaration || it is KoObjectDeclaration ||
                    it is KoFunctionDeclaration || it is KoPropertyDeclaration
            }
            .filter { it.isFeatureModule() }
            .filterNot { it.isPrivate() }
            .filterNot { it.isInsideFunction() }
            .filterNot { exempt(it) || ArchitectureExceptions.isIgnored(it) }
            .map { decl -> decl to allConstructs.map { it.evaluate(decl) } }
            .filterNot { (_, evals) -> evals.count { it.isAllRequirementsMet } == 1 }
            .map { (decl, evals) -> Violation(decl, ConstructDefinition.createDebugMessage(decl, evals)) }
    }

/**
 * The exhaustiveness check for a layer, expressed as a [ScopeCheck]. Mirrors the original
 * `KoScope.validateLayer`: every top-level declaration in [pkg] must match exactly one construct;
 * partial matches get the rich `createDebugMessage` breakdown.
 */
internal fun exhaustivenessCheck(pkg: String, constructs: List<Construct>): ScopeCheck =
    ScopeCheck { scope, exempt ->
        scope.declarations(includeNested = false)
            .filterNot { it is KoFileDeclaration }
            // Only class/interface/object/function/property declarations are classified by
            // constructs — imports, package directives etc. are not (mirrors the original).
            .filter {
                it is KoClassDeclaration || it is KoInterfaceDeclaration || it is KoObjectDeclaration ||
                    it is KoFunctionDeclaration || it is KoPropertyDeclaration
            }
            .filter { LocationUtil.resideInLocation(pkg, it.containingFilePackage()) }
            .filterNot { it.isPrivate() }
            .filterNot { it.isInsideFunction() }
            .filterNot { exempt(it) || ArchitectureExceptions.isIgnored(it) }
            .map { decl -> decl to constructs.map { it.evaluate(decl) } }
            .filterNot { (_, evals) -> evals.count { it.isAllRequirementsMet } == 1 }
            .map { (decl, evals) -> Violation(decl, ConstructDefinition.createDebugMessage(decl, evals)) }
    }

private fun integrityChecks(catalog: List<RuleGroup>) {
    val all = catalog.flatMap { it.rules }
    val duplicates = all.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isNotEmpty()) fail("Duplicate rule ids in the catalog: ${duplicates.sorted()}")

    val known = all.map { it.id }.toSet()
    val dangling = all.mapNotNull { rule ->
        (rule.enforcement as? DelegatedConstraint)?.by
            ?.filterNot { it in known }
            ?.takeIf { it.isNotEmpty() }
            ?.let { rule.id to it }
    }
    if (dangling.isNotEmpty()) fail("enforcedBy(...) references unknown rule ids: $dangling")
}

private fun render(findings: List<Finding>): String = buildString {
    appendLine("Architecture verification failed — ${findings.size} violation(s):")
    findings.groupBy { it.rule }
        .toList()
        .sortedBy { (rule, _) -> rule.id }
        .forEach { (rule, group) ->
            appendLine()
            appendLine("[${rule.id}] ${rule.title}")
            if (rule.rationale.isNotBlank()) {
                appendLine(rule.rationale.trim().prependIndent("    "))
            }
            group.forEach { appendLine("  - ${it.where}: ${it.message}") }
        }
}
