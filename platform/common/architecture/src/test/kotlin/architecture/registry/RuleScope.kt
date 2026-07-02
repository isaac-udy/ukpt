package architecture.registry

/**
 * Common `rule { … }` block receiver. Each builder RETURNS the [Enforcement], so a rule block ends in
 * exactly one of `scope`/`moduleGraph`/`constrain`/`enforcedBy`/`codegen`. Advisory conventions are
 * not rules — declare them with the `guidance(…)` delegate instead.
 */
abstract class BaseRuleScope internal constructor() {
    internal var rationaleText: String = ""
    internal val notes = mutableListOf<String>()

    /** The "why" — surfaced in failure messages and the README. */
    fun rationale(text: String) {
        rationaleText = text
    }

    fun note(text: String) {
        notes += text
    }

    /** Tested over the whole Konsist scope. */
    fun scope(check: ScopeCheck): Enforcement = ScopeConstraint(check)

    /** Tested, but enforced transitively by the rules it names. */
    fun enforcedBy(vararg ruleIds: String): Enforcement = DelegatedConstraint(ruleIds.toList())
    fun enforcedBy(vararg rules: Rule): Enforcement = DelegatedConstraint { rules.map { it.id } }

    /** Guaranteed by the postgres code generator — nothing in `src/` for Konsist to scan. */
    fun codegen(): Enforcement = NotEnforced(Tag.CODEGEN)

    /**
     * A mandatory rule that static analysis can't reliably check — enforced by review. The docs
     * carry an automatic "not automatically verifiable" note.
     */
    fun unverifiable(): Enforcement = NotEnforced(Tag.UNVERIFIABLE)
}

/** Block receiver for a group-level `rule { }`. */
class RuleScope internal constructor() : BaseRuleScope() {
    /** Tested over the parsed module dependency graph. */
    fun moduleGraph(check: ModuleGraphCheck): Enforcement = ModuleGraphConstraint(check)

    /** An unverifiable rule with an audit: the suite reports (never fails) where it looks violated. */
    fun unverifiable(audit: ScopeCheck): Enforcement = NotEnforced(Tag.UNVERIFIABLE, ScopeConstraint(audit))
}

/**
 * Block receiver for a `guidance { }` declaration. Guidance is never machine-*enforced*, but it may
 * declare an optional **audit** — a check the suite runs and reports (always passing), so soft
 * conventions stay visible without failing the build.
 */
abstract class BaseGuidanceScope internal constructor() {
    internal var rationaleText: String = ""
    internal val notes = mutableListOf<String>()
    internal var audit: Enforcement? = null

    fun rationale(text: String) {
        rationaleText = text
    }

    fun note(text: String) {
        notes += text
    }
}

/** Group-level guidance: audits run over the whole Konsist scope or the module graph. */
class GuidanceScope internal constructor() : BaseGuidanceScope() {
    /** Report (never fail) wherever this guidance is not being followed. */
    fun audit(check: ScopeCheck) {
        audit = ScopeConstraint(check)
    }

    /** Report (never fail) module-graph edges where this guidance is not being followed. */
    fun auditModuleGraph(check: ModuleGraphCheck) {
        audit = ModuleGraphConstraint(check)
    }
}

/** Construct-level guidance: the audit runs over only the declarations this construct classifies. */
class ConstructGuidanceScope internal constructor(private val construct: Construct<*>) : BaseGuidanceScope() {
    /** Report (never fail) each classified declaration where this guidance is not being followed. */
    fun audit(check: ConstructCheck) {
        audit = ScopeConstraint { scope, exempt ->
            scope.declarations(includeNested = false)
                .filter { construct.test(it) }
                .filterNot { exempt(it) }
                .flatMap { check.run(it, exempt) }
        }
    }
}

/** Block receiver for a construct's `rule { }` — adds [constrain], scoped to the construct's population. */
class ConstructRuleScope internal constructor(private val construct: Construct<*>) : BaseRuleScope() {
    /** A check over only the declarations this construct classifies. */
    fun constrain(check: ConstructCheck): Enforcement = ScopeConstraint { scope, exempt ->
        scope.declarations(includeNested = false)
            .filter { construct.test(it) }
            .filterNot { exempt(it) }
            .flatMap { check.run(it, exempt) }
    }

    /** An unverifiable rule with an audit over the construct's population (reported, never failing). */
    fun unverifiable(audit: ConstructCheck): Enforcement = NotEnforced(
        Tag.UNVERIFIABLE,
        ScopeConstraint { scope, exempt ->
            scope.declarations(includeNested = false)
                .filter { construct.test(it) }
                .filterNot { exempt(it) }
                .flatMap { audit.run(it, exempt) }
        },
    )
}
