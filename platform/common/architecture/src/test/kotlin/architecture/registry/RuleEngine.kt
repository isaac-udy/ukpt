package architecture.registry

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoParentProvider
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/*
 * Object-based rule registry.
 *
 *   object DataLayer : RuleGroup(inPackage = "feature..data..") {
 *       object Repository : Construct(isClass, hasNameEndingWith("Repository"), internal) {
 *           val propertiesEagerlyInitialized by rule("…") { constrain { decl, _ -> … } }
 *       }
 *       val noUiDeps by rule("…") { scope { scope, exempt -> … } }
 *   }
 *
 * Groups/constructs are `object`s, so cross-layer references are direct compile-time calls
 * (`DomainLayer.DomainInterface.test(x)`). Requirements (the `🔶 construct` classification) are the
 * composable predicate list in the `Construct(...)` header — AND-composed, no ids. Rules ("what it
 * DOES") are `val x by rule(...)` and take their id from the exact object/property names. Constructs
 * are discovered by reflection — no explicit list.
 */

/**
 * A classification predicate with a human description. AND-composed into a [Construct]; never an id.
 * [matches] evaluates the predicate defensively: a predicate that uses `require(decl is X)` as a type
 * guard throws when the declaration is the wrong kind — that means "not this construct", i.e. `false`.
 */
class Requirement(val description: String, val predicate: (KoBaseDeclaration) -> Boolean) {
    fun matches(declaration: KoBaseDeclaration): Boolean = runCatching { predicate(declaration) }.getOrDefault(false)
}

/** "DataLayer.Repository.ruleName" / "DataLayer.Repository" / "DataLayer.ruleName" — exact names. */
private fun pathOf(owner: Any, leaf: String?): String {
    val self = owner::class.simpleName
    val enclosing = owner::class.java.enclosingClass?.simpleName
    return listOfNotNull(enclosing, self, leaf).joinToString(".")
}

// ---- Rule scopes (the `rule { … }` block receivers) --------------------------------------------

abstract class BaseRuleScope internal constructor() {
    internal var rationaleText: String = ""
    internal val notes = mutableListOf<String>()

    /** The "why" — surfaced in failure messages and the README. */
    fun rationale(text: String) { rationaleText = text }
    fun note(text: String) { notes += text }

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

// ---- Group / construct base classes ------------------------------------------------------------

abstract class RuleContainer internal constructor() {
    internal val declaredRules = mutableListOf<Rule>()
}

/** A classifying construct: `object Repository : Construct(isClass, hasNameEndingWith("Repository"))`. */
abstract class Construct(vararg requirements: Requirement) : RuleContainer() {
    private val ownRequirements: List<Requirement> = requirements.toList()

    /** Set from the enclosing group's `inPackage` during [assemble]; folded into [requirements]. */
    internal var packageGate: String? = null

    val id: String get() = pathOf(this, null)

    /** The group package gate (if any) plus the declared requirements, AND-composed. */
    val requirements: List<Requirement>
        get() = listOfNotNull(packageGate?.let { isInPackage(it) }) + ownRequirements

    fun test(declaration: KoBaseDeclaration?): Boolean =
        declaration != null && requirements.all { it.matches(declaration) }

    protected fun rule(
        statement: String,
        block: ConstructRuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<Construct, ReadOnlyProperty<Construct, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = ConstructRuleScope(this)
            val enforcement = scope.block()
            val rule = Rule(pathOf(this, property.name), statement, scope.rationaleText, enforcement, Status.Active, scope.notes.toList())
            declaredRules += rule
            ReadOnlyProperty { _, _ -> rule }
        }
}

/** A rule group / layer: `object DataLayer : RuleGroup(inPackage = "feature..data..")`. */
abstract class RuleGroup(val inPackage: String? = null) : RuleContainer() {
    val id: String get() = this::class.simpleName ?: "?"

    /** Nested `object`s that are [Construct]s, discovered by reflection — no explicit list. */
    val constructs: List<Construct> by lazy {
        this::class.nestedClasses.mapNotNull { it.objectInstance }.filterIsInstance<Construct>()
    }

    protected fun rule(
        statement: String,
        block: RuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<RuleGroup, ReadOnlyProperty<RuleGroup, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = RuleScope()
            val enforcement = scope.block()
            val rule = Rule(pathOf(this, property.name), statement, scope.rationaleText, enforcement, Status.Active, scope.notes.toList())
            declaredRules += rule
            ReadOnlyProperty { _, _ -> rule }
        }
}

/** Force construct discovery + rule registration, and wire each group's `inPackage` gate. */
internal fun prepare(groups: List<RuleGroup>) {
    groups.forEach { group -> group.constructs.forEach { it.packageGate = group.inPackage } }
}

// ---- Requirement vocabulary --------------------------------------------------------------------
// Convention: a parameterless requirement is a `val` (reads cleanly inside `oneOf(isClass, isObject)`);
// one that needs an argument is a `fun`. A builder may offer both under one name — Kotlin lets a
// property and a same-named function coexist — e.g. a no-arg default plus a parameterised form.
// Type/state checks are `is…`; the member/relational ones are `hasNameEndingWith` / `extends`.

private fun KoBaseDeclaration.hasMod(modifier: KoModifier): Boolean = when (this) {
    is KoClassDeclaration -> hasModifier(modifier)
    is KoInterfaceDeclaration -> hasModifier(modifier)
    is KoObjectDeclaration -> hasModifier(modifier)
    is KoFunctionDeclaration -> hasModifier(modifier)
    is KoPropertyDeclaration -> hasModifier(modifier)
    else -> false
}

val isClass = Requirement("is a class") { it is KoClassDeclaration }
val isInterface = Requirement("is an interface") { it is KoInterfaceDeclaration }
val isObject = Requirement("is an object") { it is KoObjectDeclaration }
val isFunction = Requirement("is a function") { it is KoFunctionDeclaration }
val isProperty = Requirement("is a property") { it is KoPropertyDeclaration }
val isClassOrObject = Requirement("is a class or object") { it is KoClassDeclaration || it is KoObjectDeclaration }
val isClassOrInterface = Requirement("is a class or interface") { it is KoClassDeclaration || it is KoInterfaceDeclaration }
val isDataClass = Requirement("is a `data class`") { it.hasMod(KoModifier.DATA) }
val isEnum = Requirement("is an `enum class`") { it.hasMod(KoModifier.ENUM) }
val isSealed = Requirement("is `sealed`") { it.hasMod(KoModifier.SEALED) }
val isValueClass = Requirement("is a `value class`") { it.hasMod(KoModifier.VALUE) }
val isAbstract = Requirement("is `abstract`") { it.hasMod(KoModifier.ABSTRACT) }
val isInternal = Requirement("is `internal`") { it.hasMod(KoModifier.INTERNAL) }
val isFunInterface = Requirement("is a `fun interface`") { it is KoInterfaceDeclaration && it.hasMod(KoModifier.FUN) }

/** Has any annotation at all; `isAnnotatedWith(name)` narrows to a specific one. */
val isAnnotated = Requirement("has any annotation") { (it as? KoAnnotationProvider)?.annotations?.isNotEmpty() == true }

fun isInPackage(glob: String) = Requirement("resides in `$glob`") { it.residesIn(glob) }
fun isAnnotatedWith(name: String) =
    Requirement("annotated `@$name`") { (it as? KoAnnotationProvider)?.hasAnnotationWithName(name) == true }
fun hasNameEndingWith(suffix: String) =
    Requirement("name ends with `$suffix`") { (it as? KoNameProvider)?.name?.endsWith(suffix) == true }
fun extends(parentName: String) =
    Requirement("extends `$parentName`") { d -> (d as? KoParentProvider)?.parents()?.any { it.name == parentName } == true }

fun oneOf(vararg options: Requirement) =
    Requirement("one of {${options.joinToString(", ") { it.description }}}") { d -> options.any { it.matches(d) } }

fun not(requirement: Requirement) =
    Requirement("not ${requirement.description}") { !requirement.matches(it) }

/** Escape hatch for a one-off classification predicate the vocabulary doesn't cover. */
fun predicate(description: String, test: (KoBaseDeclaration) -> Boolean) = Requirement(description, test)

// Typed escape hatches — the predicate only matches (and only runs) for that declaration kind.
fun isClassWhere(description: String, test: (KoClassDeclaration) -> Boolean) =
    Requirement(description) { (it as? KoClassDeclaration)?.let(test) == true }
fun isInterfaceWhere(description: String, test: (KoInterfaceDeclaration) -> Boolean) =
    Requirement(description) { (it as? KoInterfaceDeclaration)?.let(test) == true }
fun isObjectWhere(description: String, test: (KoObjectDeclaration) -> Boolean) =
    Requirement(description) { (it as? KoObjectDeclaration)?.let(test) == true }
fun isFunctionWhere(description: String, test: (KoFunctionDeclaration) -> Boolean) =
    Requirement(description) { (it as? KoFunctionDeclaration)?.let(test) == true }
fun isPropertyWhere(description: String, test: (KoPropertyDeclaration) -> Boolean) =
    Requirement(description) { (it as? KoPropertyDeclaration)?.let(test) == true }
