package architecture.spike

import architecture.registry.Enforcement
import architecture.registry.Rule
import architecture.registry.RuleScope
import architecture.registry.Status
import architecture.registry.residesIn
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
 * SPIKE — an object-based take on the rule registry, to compare against the live `architecture.rules`
 * catalog. The live engine is untouched; this is parallel and demonstrative.
 *
 * Two ideas being tried:
 *   1. Groups and constructs are `object`s (`object DataLayer : RuleGroup { object Repository : Construct }`),
 *      so cross-layer references are direct compile-time calls (`DomainLayer.DomainInterface.test(x)`) —
 *      the `Classifiers` indirection disappears.
 *   2. Requirements are a composable predicate list passed to the `Construct` constructor (no individual
 *      ids — they classify, AND-composed). Rules ("what it DOES") stay `val x by rule(...)` in the body
 *      and keep their ids. Rule ids use the exact object/property names: `DomainLayer.DomainObject.immutable`.
 *
 * Constructs are discovered by reflection (kotlin-reflect) — no explicit `listOf`.
 */

/** A classification predicate with a human description. AND-composed into a [Construct]; never an id. */
class Requirement(val description: String, val predicate: (KoBaseDeclaration) -> Boolean)

/** "DataLayer.Repository.ruleName" / "DataLayer.Repository" / "DataLayer.ruleName" — exact names. */
private fun pathOf(owner: Any, leaf: String?): String {
    val self = owner::class.simpleName
    val enclosing = owner::class.java.enclosingClass?.simpleName
    return listOfNotNull(enclosing, self, leaf).joinToString(".")
}

/** Shared base for anything that declares `val x by rule("…") { … }` (a group or a construct). */
abstract class RuleContainer {
    internal val declaredRules = mutableListOf<Rule>()

    protected fun rule(
        statement: String,
        block: RuleScope.() -> Enforcement,
    ): PropertyDelegateProvider<RuleContainer, ReadOnlyProperty<RuleContainer, Rule>> {
        val owner = this
        return PropertyDelegateProvider { _, property ->
            val scope = RuleScope()
            val enforcement = scope.block()
            val rule = Rule(
                id = pathOf(owner, property.name),
                title = statement,
                rationale = scope.rationaleText,
                enforcement = enforcement,
                status = Status.Active,
                notes = scope.notes.toList(),
            )
            owner.declaredRules += rule
            ReadOnlyProperty { _, _ -> rule }
        }
    }
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
        declaration != null && requirements.all { it.predicate(declaration) }
}

/** A rule group / layer: `object DataLayer : RuleGroup(inPackage = "feature..data..")`. */
abstract class RuleGroup(val inPackage: String? = null) : RuleContainer() {
    val id: String get() = this::class.simpleName ?: "?"

    /** Nested `object`s that are [Construct]s, discovered by reflection — no explicit list. */
    val constructs: List<Construct> by lazy {
        this::class.nestedClasses
            .mapNotNull { it.objectInstance }
            .filterIsInstance<Construct>()
    }
}

/**
 * Wire each group's `inPackage` gate into its constructs (forcing construct discovery + rule
 * registration) and return every rule in declaration order: group-level rules, then each
 * construct's rules.
 */
fun assemble(groups: List<RuleGroup>): List<Rule> {
    groups.forEach { group -> group.constructs.forEach { it.packageGate = group.inPackage } }
    return groups.flatMap { group ->
        group.declaredRules + group.constructs.flatMap { it.declaredRules }
    }
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
val isDataClass = Requirement("is a `data class`") { it.hasMod(KoModifier.DATA) }
val isEnum = Requirement("is an `enum class`") { it.hasMod(KoModifier.ENUM) }
val isSealed = Requirement("is `sealed`") { it.hasMod(KoModifier.SEALED) }
val isValueClass = Requirement("is a `value class`") { it.hasMod(KoModifier.VALUE) }
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
    Requirement("one of {${options.joinToString(", ") { it.description }}}") { d -> options.any { it.predicate(d) } }

fun not(requirement: Requirement) =
    Requirement("not ${requirement.description}") { !requirement.predicate(it) }

/** Escape hatch for a one-off classification predicate the vocabulary doesn't cover. */
fun predicate(description: String, test: (KoBaseDeclaration) -> Boolean) = Requirement(description, test)
