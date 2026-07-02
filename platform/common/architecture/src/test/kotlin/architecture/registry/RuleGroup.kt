package architecture.registry

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/*
 * Object-based rule registry.
 *
 *   @Describe("The client-only data axis: Repositories and local persistence.")
 *   object DataLayer : RuleGroup(inPackage = "feature..data..") {
 *       @Describe("A class providing implementations for domain interfaces.")
 *       object Repository : Construct(
 *           requirements = listOf(isClass, hasNameEndingWith("Repository")),
 *       ) {
 *           @Describe("Repository properties must be initialized immediately")
 *           val propertiesEagerlyInitialized by rule { constrain { decl, _ -> … } }
 *
 *           @Describe("May inject Services or Storage objects")
 *           val mayInjectServices by guidance
 *       }
 *   }
 *
 * Groups/constructs are `object`s, so cross-layer references are direct compile-time calls
 * (`DomainLayer.DomainInterface.test(x)`). Requirements (the construct classification) are the
 * `requirements` list in the `Construct(...)` header — AND-composed, no ids. Rules ("what it DOES")
 * are `val x by rule { … }`; advisory conventions are `val x by guidance` (or `by guidance { … }`
 * for notes/rationale). Statements and descriptions come from `@Describe`; ids from the exact
 * object/property names. Constructs are discovered by reflection.
 */

/** "DataLayer.Repository.ruleName" / "DataLayer.Repository" / "DataLayer.ruleName" — exact names. */
private fun pathOf(owner: Any, leaf: String?): String {
    val self = owner::class.simpleName
    val enclosing = owner::class.java.enclosingClass?.simpleName
    return listOfNotNull(enclosing, self, leaf).joinToString(".")
}

/** A rule/guidance property's statement is its [Describe] text — required. */
private fun KProperty<*>.statementOrFail(owner: Any): String =
    describeText() ?: error("${pathOf(owner, name)}: rule/guidance declarations need @Describe(\"…\") with the statement")

abstract class RuleContainer internal constructor() {
    internal val declaredRules = mutableListOf<Rule>()
}

/** A classifying construct: `object Repository : Construct(requirements = listOf(isClass, …))`. */
abstract class Construct(requirements: List<Requirement>) : RuleContainer() {
    private val ownRequirements: List<Requirement> = requirements

    /** Set from the enclosing group's `inPackage` during [prepare]; folded into [requirements]. */
    internal var packageGate: String? = null

    val id: String get() = pathOf(this, null)

    /** The group package gate (if any) plus the declared requirements, AND-composed. */
    val requirements: List<Requirement>
        get() = listOfNotNull(packageGate?.let { isInPackage(it) }) + ownRequirements

    fun test(declaration: KoBaseDeclaration?): Boolean =
        declaration != null && requirements.all { it.matches(declaration) }

    /** An enforced rule; the statement comes from the property's [Describe]. */
    protected fun rule(
        block: ConstructRuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<Construct, ReadOnlyProperty<Construct, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = ConstructRuleScope(this)
            val enforcement = scope.block()
            register(Rule(pathOf(this, property.name), property.statementOrFail(this), scope.rationaleText, enforcement, Status.Active, scope.notes.toList()))
        }

    /** An advisory convention with context: `@Describe("…") val x by guidance { note("…") }`. */
    protected fun guidance(
        block: GuidanceScope.() -> Unit,
    ): PropertyDelegateProvider<Construct, ReadOnlyProperty<Construct, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = GuidanceScope().apply(block)
            register(Rule(pathOf(this, property.name), property.statementOrFail(this), scope.rationaleText, NotEnforced(Tag.GUIDANCE), Status.Active, scope.notes.toList()))
        }

    /** An advisory convention: `@Describe("…") val x by guidance`. */
    protected val guidance: PropertyDelegateProvider<Construct, ReadOnlyProperty<Construct, Rule>>
        get() = guidance {}

    private fun register(rule: Rule): ReadOnlyProperty<Construct, Rule> {
        declaredRules += rule
        return ReadOnlyProperty { _, _ -> rule }
    }
}

/** A rule group / layer: `object DataLayer : RuleGroup(inPackage = "feature..data..")`. */
abstract class RuleGroup(val inPackage: String? = null) : RuleContainer() {
    val id: String get() = this::class.simpleName ?: "?"

    /** Nested `object`s that are [Construct]s, discovered by reflection — no explicit list. */
    val constructs: List<Construct> by lazy {
        this::class.nestedClasses.mapNotNull { it.objectInstance }.filterIsInstance<Construct>()
    }

    /** An enforced rule; the statement comes from the property's [Describe]. */
    protected fun rule(
        block: RuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<RuleGroup, ReadOnlyProperty<RuleGroup, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = RuleScope()
            val enforcement = scope.block()
            register(Rule(pathOf(this, property.name), property.statementOrFail(this), scope.rationaleText, enforcement, Status.Active, scope.notes.toList()))
        }

    /** An advisory convention with context: `@Describe("…") val x by guidance { note("…") }`. */
    protected fun guidance(
        block: GuidanceScope.() -> Unit,
    ): PropertyDelegateProvider<RuleGroup, ReadOnlyProperty<RuleGroup, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = GuidanceScope().apply(block)
            register(Rule(pathOf(this, property.name), property.statementOrFail(this), scope.rationaleText, NotEnforced(Tag.GUIDANCE), Status.Active, scope.notes.toList()))
        }

    /** An advisory convention: `@Describe("…") val x by guidance`. */
    protected val guidance: PropertyDelegateProvider<RuleGroup, ReadOnlyProperty<RuleGroup, Rule>>
        get() = guidance {}

    private fun register(rule: Rule): ReadOnlyProperty<RuleGroup, Rule> {
        declaredRules += rule
        return ReadOnlyProperty { _, _ -> rule }
    }
}

/** Force construct discovery + rule registration, and wire each group's `inPackage` gate. */
internal fun prepare(groups: List<RuleGroup>) {
    groups.forEach { group -> group.constructs.forEach { it.packageGate = group.inPackage } }
}
