package architecture.registry

/**
 * Common `rule { … }` block receiver. Each builder RETURNS the [Enforcement], so a rule block ends in
 * exactly one of `scope`/`moduleGraph`/`constrain`/`enforcedBy`/`guidance`/`codegen`.
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

    /** ✅ tested over the whole Konsist scope. */
    fun scope(check: ScopeCheck): Enforcement = ScopeConstraint(check)

    /** ✅ tested, but enforced transitively by the rules it names. */
    fun enforcedBy(vararg ruleIds: String): Enforcement = DelegatedConstraint(ruleIds.toList())
    fun enforcedBy(vararg rules: Rule): Enforcement = DelegatedConstraint(rules.map { it.id })
    fun guidance(): Enforcement = NotEnforced(Tag.GUIDANCE)
    fun codegen(): Enforcement = NotEnforced(Tag.CODEGEN)
}

/** Block receiver for a group-level `rule { }`. */
class RuleScope internal constructor() : BaseRuleScope() {
    /** ✅ tested over the parsed module dependency graph. */
    fun moduleGraph(check: ModuleGraphCheck): Enforcement = ModuleGraphConstraint(check)
}

/** Block receiver for a construct's `rule { }` — adds [constrain], scoped to the construct's population. */
class ConstructRuleScope internal constructor(private val construct: Construct) : BaseRuleScope() {
    /** A check over only the declarations this construct classifies. */
    fun constrain(check: ConstructCheck): Enforcement = ScopeConstraint { scope, exempt ->
        scope.declarations(includeNested = false)
            .filter { construct.test(it) }
            .filterNot { exempt(it) }
            .flatMap { check.run(it, exempt) }
    }
}
