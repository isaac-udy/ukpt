package architecture.registry

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import java.lang.reflect.ParameterizedType
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/*
 * Object-based rule registry.
 *
 *   // DataLayer.Repository.kt
 *   @Describe("A class providing implementations for domain interfaces.")
 *   object Repository : Construct<DataLayer>(
 *       requirements = listOf(isClass, hasNameEndingWith("Repository")),
 *   ) {
 *       @Describe("Repository properties must be initialized immediately")
 *       val propertiesEagerlyInitialized by rule { constrain { decl, _ -> … } }
 *   }
 *
 *   // DataLayer.kt — the layer's manifest
 *   @Describe("The client-only data axis: Repositories and local persistence.")
 *   object DataLayer : RuleGroup(
 *       inPackage = "feature..data..",
 *       constructs = listOf(Repository, ClientStorage),
 *   )
 *
 * Constructs are top-level `object`s, one per file. The `Construct<Group>` type argument carries
 * ownership (and the id prefix); the group's `constructs` list carries discovery and the order the
 * docs render in. The two are deliberately redundant — an integrity check fails when they disagree,
 * and a Konsist meta-rule over this module's own sources fails when a declared construct isn't
 * listed. Statements and descriptions come from `@Describe`; ids from the exact object/property
 * names, resolved lazily (rules register while the group's `constructs` list is still evaluating).
 */

/** A rule/guidance property's statement is its [Describe] text — required. */
private fun KProperty<*>.statementOrFail(owner: Any): String =
    describeText() ?: error("${owner.javaClass.simpleName}.$name: rule/guidance declarations need @Describe(\"…\") with the statement")

abstract class RuleContainer internal constructor() {
    internal val declaredRules = mutableListOf<Rule>()

    /** The dotted path id — resolved lazily; do not read during object initialization. */
    abstract val id: String
}

/**
 * A classifying construct: a top-level `object X : Construct<Group>(requirements = listOf(…))`,
 * one per file, listed by its group's `constructs`.
 */
abstract class Construct<G : RuleGroup>(requirements: List<Requirement>) : RuleContainer() {
    private val ownRequirements: List<Requirement> = requirements

    /** Set from the owning group's `inPackage` during [prepare]; folded into [requirements]. */
    internal var packageGate: String? = null

    /** The owning group, resolved from the declaration's `Construct<Group>` type argument. */
    val owner: RuleGroup by lazy {
        val parameterized = javaClass.genericSuperclass as? ParameterizedType
            ?: error("${javaClass.simpleName}: a construct must be declared as `object X : Construct<Group>(…)`")
        val groupClass = parameterized.actualTypeArguments.firstOrNull() as? Class<*>
            ?: error("${javaClass.simpleName}: could not resolve the construct's group type argument")
        groupClass.kotlin.objectInstance as? RuleGroup
            ?: error("${javaClass.simpleName}: the `Construct<…>` type argument must be a `RuleGroup` object")
    }

    override val id: String get() = "${owner.id}.${this::class.simpleName}"

    /** The group package gate (if any) plus the declared requirements, AND-composed. */
    val requirements: List<Requirement>
        get() = listOfNotNull(packageGate?.let { isInPackage(it) }) + ownRequirements

    fun test(declaration: KoBaseDeclaration?): Boolean =
        declaration != null && requirements.all { it.matches(declaration) }

    /** An enforced rule; the statement comes from the property's [Describe]. */
    protected fun rule(
        block: ConstructRuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<Construct<G>, ReadOnlyProperty<Construct<G>, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = ConstructRuleScope(this)
            val enforcement = scope.block()
            register(Rule({ "$id.${property.name}" }, property.statementOrFail(this), scope.rationaleText, enforcement, Status.Active, scope.notes.toList()))
        }

    /** An advisory convention with context: `@Describe("…") val x by guidance { note("…") }`. */
    protected fun guidance(
        block: GuidanceScope.() -> Unit,
    ): PropertyDelegateProvider<Construct<G>, ReadOnlyProperty<Construct<G>, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = GuidanceScope().apply(block)
            register(Rule({ "$id.${property.name}" }, property.statementOrFail(this), scope.rationaleText, NotEnforced(Tag.GUIDANCE), Status.Active, scope.notes.toList()))
        }

    /** An advisory convention: `@Describe("…") val x by guidance`. */
    protected val guidance: PropertyDelegateProvider<Construct<G>, ReadOnlyProperty<Construct<G>, Rule>>
        get() = guidance {}

    private fun register(rule: Rule): ReadOnlyProperty<Construct<G>, Rule> {
        declaredRules += rule
        return ReadOnlyProperty { _, _ -> rule }
    }
}

/**
 * A rule group / layer: `object DataLayer : RuleGroup(inPackage = "…", constructs = listOf(…))`.
 * The `constructs` list is the discovery mechanism AND the order the docs render in.
 */
abstract class RuleGroup(
    val inPackage: String? = null,
    val constructs: List<Construct<*>> = emptyList(),
) : RuleContainer() {
    override val id: String get() = this::class.simpleName ?: "?"

    /** An enforced rule; the statement comes from the property's [Describe]. */
    protected fun rule(
        block: RuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<RuleGroup, ReadOnlyProperty<RuleGroup, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = RuleScope()
            val enforcement = scope.block()
            register(Rule({ "$id.${property.name}" }, property.statementOrFail(this), scope.rationaleText, enforcement, Status.Active, scope.notes.toList()))
        }

    /** An advisory convention with context: `@Describe("…") val x by guidance { note("…") }`. */
    protected fun guidance(
        block: GuidanceScope.() -> Unit,
    ): PropertyDelegateProvider<RuleGroup, ReadOnlyProperty<RuleGroup, Rule>> =
        PropertyDelegateProvider { _, property ->
            val scope = GuidanceScope().apply(block)
            register(Rule({ "$id.${property.name}" }, property.statementOrFail(this), scope.rationaleText, NotEnforced(Tag.GUIDANCE), Status.Active, scope.notes.toList()))
        }

    /** An advisory convention: `@Describe("…") val x by guidance`. */
    protected val guidance: PropertyDelegateProvider<RuleGroup, ReadOnlyProperty<RuleGroup, Rule>>
        get() = guidance {}

    private fun register(rule: Rule): ReadOnlyProperty<RuleGroup, Rule> {
        declaredRules += rule
        return ReadOnlyProperty { _, _ -> rule }
    }
}

/** Wire each group's `inPackage` gate into its constructs and check the construct↔group links. */
internal fun prepare(groups: List<RuleGroup>) {
    groups.forEach { group ->
        group.constructs.forEach { construct ->
            check(construct.owner === group) {
                "${construct.owner.id}.${construct::class.simpleName} is listed by ${group.id}'s constructs, " +
                    "but its `Construct<…>` type argument names ${construct.owner.id}"
            }
            construct.packageGate = group.inPackage
        }
    }
    val duplicates = groups.flatMap { it.constructs }.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    check(duplicates.isEmpty()) { "Constructs listed more than once: ${duplicates.sorted()}" }
}
