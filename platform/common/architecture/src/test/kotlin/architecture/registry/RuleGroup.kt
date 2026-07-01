package architecture.registry

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/*
 * Object-based rule registry.
 *
 *   object DataLayer : RuleGroup(inPackage = "feature..data..") {
 *       object Repository : Construct(isClass, hasNameEndingWith("Repository"), isInternal) {
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

/** "DataLayer.Repository.ruleName" / "DataLayer.Repository" / "DataLayer.ruleName" — exact names. */
private fun pathOf(owner: Any, leaf: String?): String {
    val self = owner::class.simpleName
    val enclosing = owner::class.java.enclosingClass?.simpleName
    return listOfNotNull(enclosing, self, leaf).joinToString(".")
}

abstract class RuleContainer internal constructor() {
    internal val declaredRules = mutableListOf<Rule>()
}

/** A classifying construct: `object Repository : Construct(isClass, hasNameEndingWith("Repository"))`. */
abstract class Construct(vararg requirements: Requirement) : RuleContainer() {
    private val ownRequirements: List<Requirement> = requirements.toList()

    /** Set from the enclosing group's `inPackage` during [prepare]; folded into [requirements]. */
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
