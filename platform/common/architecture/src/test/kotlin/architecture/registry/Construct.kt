package architecture.registry

import architecture.definitions.ConstructDefinition
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration

/**
 * A named, classifying shape (e.g. "Domain Interface"). It owns two kinds of rule:
 *  - [requirements] — define *what it means to be* this construct (classification predicates,
 *    AND-composed; used by the exhaustiveness check and as a population selector).
 *  - [rules] — verify the *functionality* of declarations that are this construct (construct-scoped
 *    constraints and guidance), e.g. "a UseCase must not override its interface's defaults".
 *
 * The proven `ConstructDefinition` evaluator (with `EvaluationResult` / `createDebugMessage`) is
 * reused for classification; a construct is a *derived classifier* (`test`/`evaluate`), never a
 * verdict — every declaration fails most constructs by design.
 */
class Construct internal constructor(
    val id: String,                               // path, e.g. "domainLayer.domainInterface"
    val name: String,                             // humanised, e.g. "Domain Interface"
    val requirements: List<Rule>,                 // classification — each enforcement is a ShapeRequirement
    val rules: List<Rule>,                        // functionality — construct-scoped constraints / guidance
    internal val definition: ConstructDefinition,
) {
    fun test(declaration: KoBaseDeclaration?): Boolean = definition.test(declaration)

    fun evaluate(declaration: KoBaseDeclaration?): ConstructDefinition.EvaluationResult =
        definition.evaluate(declaration)
}
