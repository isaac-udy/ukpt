package architecture.registry

import architecture.ArchitectureExceptions
import architecture.definitions.isFeatureModule
import architecture.definitions.isInsideFunction
import architecture.definitions.isPrivate
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

/** Every top-level declaration in a layer's package must match exactly one of its constructs. */
internal fun exhaustiveRule(group: RuleGroup): Rule = Rule(
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

/** Cross-layer: every feature-module declaration must match exactly one construct across all layers. */
internal fun membershipRule(groups: List<RuleGroup>): Rule = Rule(
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
private fun membershipCheck(constructs: List<Construct<*>>, pkg: String?): ScopeCheck =
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
private fun classifyMessage(declaration: KoBaseDeclaration, constructs: List<Construct<*>>): String {
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
