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

//Phase 2a is essentially done — here's where it stands
//
//Still fully uncommitted (HEAD unchanged at 87494d2). The architecture-enforcement subsystem is now entirely the registry:
//
//- All 7 groups (domainLayer, uiLayer, dataLayer, servicesLayer, featureRules, projectRules, moduleRules) + per-layer exhaustiveness + the global membership check, ~2,450 lines of engine + catalog.
//- Legacy fully deleted — 6 test classes + 5 *Layer definitions + LayerDefinition + validateLayer gone; definitions/ is just the reusable engine (ConstructDefinition, DefinitionPredicate, helper extensions).
//- verify() is the sole arch test, green. The migration cross-check earned its keep — it caught two real engine bugs (imports leaking into the exhaustiveness check; constructs missing their package gate, which broke the cross-layer union) that a blind rewrite would've shipped.
//- IDs are clean paths (domainLayer.useCase.noOverridingDefaults), cross-layer refs go through the lazy Classifiers accessor (no old-*Layer deps), and R-UI-33 (the one genuinely-lost enforcement) was ported to projectRules.
//
//What's left in #12 — and one sizing decision
//
//1. README index generation + golden drift test (the doc↔registry sync) + an updateRuleIndex task — mechanical, clearly worth doing. I'll do this.
//2. Hygiene — drop the konsist.core.util.LocationUtil internal (4 files) for the public package API, and pin kotlin-reflect to 2.4.0. Small, I'll do this.
//3. The README prose reconcile — this is the scope decision. You earlier chose "fully reconcile the README," but that was before we moved to path IDs. The 857-line README is written entirely around R-<axis>-NN (every rule statement, every [R-DATA-06] cross-reference). Reconciling to path IDs is no longer a renumber — it's a full rewrite of every rule reference in the doc, plus deciding how much prose stays vs. gets generated.
//
//So before I sink into the README: do you want
//
//- (A) the lighter pass now — generate the rule index (path-id │ tag │ title) into a delimited block with a golden drift test, do the hygiene, and leave the existing R-<axis>-NN prose in place for a follow-up; or
//- (B) the full reconcile — rewrite the whole README to path IDs (index + every statement + every cross-reference), which is a large doc sweep?
//
//I'd lean (A) for now (it delivers the doc↔registry sync + keeps this reviewable), with the full prose rewrite as a deliberate follow-up — but it's your call since you'd asked for the full reconcile. Which way?
