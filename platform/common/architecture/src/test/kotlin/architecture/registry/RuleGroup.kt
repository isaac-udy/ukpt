package architecture.registry

import architecture.definitions.ConstructDefinition
import architecture.definitions.DefinitionPredicate
import architecture.definitions.containingFilePackage
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.core.util.LocationUtil
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * An ordered group of rules, declared as `val xxx by rules { … }`. The property name is the
 * group's path root and (humanised) display name. Supplying [pkg] via `inPackage` makes it a
 * *layer*: it gains an exhaustiveness rule (every top-level declaration in the package must match
 * exactly one construct).
 */
class RuleGroup internal constructor(
    val id: String,
    val name: String,
    val pkg: String?,
    val rules: List<Rule>,
    val constructs: List<Construct>,
)

/**
 * Declare a rule group: `val domainLayer by rules(inPackage = "feature..domain..") { … }`. The
 * `by` delegate captures the property name as the group's id/path; nested `construct`/`rule`
 * declarations extend that path with their own property names.
 */
fun rules(
    inPackage: String? = null,
    block: RuleGroupScope.() -> Unit,
): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, RuleGroup>> =
    PropertyDelegateProvider { _, property ->
        val group = RuleGroupScope(property.name, inPackage).apply(block).build()
        ReadOnlyProperty { _, _ -> group }
    }

class RuleGroupScope internal constructor(
    private val path: String,
    private val pkg: String?,
) {
    private val rules = mutableListOf<Rule>()
    private val constructs = mutableListOf<Construct>()

    /** A 🔶 construct: `val domainInterface by construct { … }` — name + path from the property. */
    fun construct(block: ConstructScope.() -> Unit): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Construct>> =
        PropertyDelegateProvider { _, property ->
            val construct = ConstructScope(path, property.name, pkg).apply(block).build()
            constructs += construct
            rules += construct.requirements   // classification
            rules += construct.rules          // functionality (construct-scoped constraints / guidance)
            ReadOnlyProperty { _, _ -> construct }
        }

    /** A standalone rule: `val noPlatformDeps by rule("…") { scope { … } }`. The block returns the enforcement. */
    fun rule(
        statement: String,
        block: RuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = RuleScope()
            val enforcement = scope.block()
            val rule = Rule(
                id = "$path.${property.name}",
                title = statement,
                rationale = scope.rationaleText,
                enforcement = enforcement,
                status = Status.Active,
                notes = scope.notes.toList(),
            )
            rules += rule
            ReadOnlyProperty { _, _ -> rule }
        }

    /** Document a removed/renamed rule: `val oldThing by retired("…")`. */
    fun retired(reason: String, replacedBy: String? = null): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Rule>> =
        PropertyDelegateProvider { _, property ->
            val rule = Rule(
                id = "$path.${property.name}",
                title = "(retired)",
                rationale = reason,
                enforcement = NotEnforced(Tag.GUIDANCE),
                status = Status.Retired(reason, replacedBy),
                notes = emptyList(),
            )
            rules += rule
            ReadOnlyProperty { _, _ -> rule }
        }

    internal fun build(): RuleGroup {
        val all = rules.toMutableList()
        if (pkg != null) {
            all += Rule(
                id = "$path.exhaustive",
                title = "Every top-level declaration in `$pkg` matches exactly one construct",
                rationale = """
                    A declaration here that matches no construct (or more than one) is either mis-placed
                    or a shape the architecture doesn't recognise. Make it conform to a construct, or add one.
                """.trimIndent(),
                enforcement = ScopeConstraint(exhaustivenessCheck(pkg, constructs.toList())),
                status = Status.Active,
                notes = emptyList(),
            )
        }
        return RuleGroup(path, humanize(path), pkg, all.toList(), constructs.toList())
    }
}

class ConstructScope internal constructor(
    private val parentPath: String,
    private val leaf: String,
    private val pkg: String?,
) {
    private val requirements = mutableListOf<Rule>()
    private val functionalityRules = mutableListOf<Rule>()
    private val predicates = mutableListOf<Pair<String, DefinitionPredicate<KoBaseDeclaration>>>()
    private var resolvedConstruct: Construct? = null

    /** A 🔶 requirement that *classifies* — defines what it means to be this construct. */
    fun requirement(
        statement: String,
        predicate: DefinitionPredicate.Companion.() -> DefinitionPredicate<KoBaseDeclaration>,
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Rule>> =
        PropertyDelegateProvider { _, property ->
            val id = "$parentPath.$leaf.${property.name}"
            val predicateInstance = DefinitionPredicate.run(predicate)
            val rule = Rule(id, statement, "", ShapeRequirement(predicateInstance), Status.Active, emptyList())
            requirements += rule
            predicates += "$id — $statement" to predicateInstance
            ReadOnlyProperty { _, _ -> rule }
        }

    /** A rule that *verifies the functionality* of this construct; `constrain { }` targets it implicitly. */
    fun rule(
        statement: String,
        block: ConstructRuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = ConstructRuleScope {
                requireNotNull(resolvedConstruct) { "construct '$parentPath.$leaf' used before it was built" }
            }
            val enforcement = scope.block()
            val rule = Rule(
                id = "$parentPath.$leaf.${property.name}",
                title = statement,
                rationale = scope.rationaleText,
                enforcement = enforcement,
                status = Status.Active,
                notes = scope.notes.toList(),
            )
            functionalityRules += rule
            ReadOnlyProperty { _, _ -> rule }
        }

    internal fun build(): Construct {
        val definition = ConstructDefinition.define(constructName = humanize(leaf)) {
            // Implicit package gate (not a catalog rule): a construct in a layer only classifies
            // declarations in that layer's package — mirrors the legacy `define`'s "Is in package"
            // requirement, so cross-layer reuse + the global membership union don't false-match.
            if (pkg != null) {
                rule("Resides in `$pkg`") { any { LocationUtil.resideInLocation(pkg, it.containingFilePackage()) } }
            }
            predicates.forEach { (name, predicateInstance) -> rule(name) { predicateInstance } }
        }
        return Construct(
            id = "$parentPath.$leaf",
            name = humanize(leaf),
            requirements = requirements.toList(),
            rules = functionalityRules.toList(),
            definition = definition,
        ).also { resolvedConstruct = it }
    }
}

/** Scope for a rule declared *inside* a construct: `constrain { }` is scoped to the enclosing construct. */
class ConstructRuleScope internal constructor(
    private val construct: () -> Construct,
) {
    internal var rationaleText: String = ""
    internal val notes = mutableListOf<String>()

    fun rationale(text: String) { rationaleText = text }
    fun note(text: String) { notes += text }

    /** A constraint over the declarations this construct classifies. */
    fun constrain(check: ConstructCheck): Enforcement = ConstructConstraint(construct, check)
    fun guidance(): Enforcement = NotEnforced(Tag.GUIDANCE)
    fun codegen(): Enforcement = NotEnforced(Tag.CODEGEN)
    fun enforcedBy(vararg rules: Rule): Enforcement = DelegatedConstraint(rules.map { it.id })
    fun enforcedBy(vararg ruleIds: String): Enforcement = DelegatedConstraint(ruleIds.toList())
}

class RuleScope internal constructor() {
    internal var rationaleText: String = ""
    internal val notes = mutableListOf<String>()

    /** The "why" — surfaced in failure messages and the README. */
    fun rationale(text: String) { rationaleText = text }
    fun note(text: String) { notes += text }

    // Each of these RETURNS the enforcement, so a rule block must end in exactly one.
    fun scope(check: ScopeCheck): Enforcement = ScopeConstraint(check)
    fun constrain(construct: Construct, check: ConstructCheck): Enforcement = ConstructConstraint({ construct }, check)
    fun moduleGraph(check: ModuleGraphCheck): Enforcement = ModuleGraphConstraint(check)
    fun enforcedBy(vararg rules: Rule): Enforcement = DelegatedConstraint(rules.map { it.id })
    fun enforcedBy(vararg ruleIds: String): Enforcement = DelegatedConstraint(ruleIds.toList())
    fun guidance(): Enforcement = NotEnforced(Tag.GUIDANCE)
    fun codegen(): Enforcement = NotEnforced(Tag.CODEGEN)
}

/** `domainInterface` → "Domain Interface"; `moduleRules` → "Module Rules". */
internal fun humanize(camelCase: String): String =
    camelCase.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
