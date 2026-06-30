package architecture.registry

import architecture.ArchitectureExceptions
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration

/**
 * Resolves rule exemptions from the two channels, both keyed by rule id:
 *  - **declarations** → the `@ArchitectureException(ruleIds = [...])` annotation, read via
 *    [ArchitectureExceptions] (annotation/file level).
 *  - **build-graph edges** → the `// architecture-exception: moduleRules.platformNotFeature` comment, already parsed
 *    onto [ModuleEdge.exemptRuleIds] (build scripts can't carry a binary-retained annotation).
 *
 * The runner curries the rule id, so each check just receives a `(subject) -> Boolean`.
 */
object Exemptions {
    fun isExempt(ruleId: String, declaration: KoBaseDeclaration): Boolean =
        ArchitectureExceptions.isExempt(declaration, ruleId)

    fun isExempt(ruleId: String, edge: ModuleEdge): Boolean =
        ruleId in edge.exemptRuleIds
}
