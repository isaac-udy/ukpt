package architecture.registry

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoParentProvider

/**
 * A classification predicate with a human description. AND-composed into a [Construct]; never an id.
 * [matches] evaluates the predicate defensively: a predicate that uses `require(decl is X)` as a type
 * guard throws when the declaration is the wrong kind — that means "not this construct", i.e. `false`.
 */
class Requirement(val description: String, val predicate: (KoBaseDeclaration) -> Boolean) {
    fun matches(declaration: KoBaseDeclaration): Boolean = runCatching { predicate(declaration) }.getOrDefault(false)
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
val isClassOrInterface =
    Requirement("is a class or interface") { it is KoClassDeclaration || it is KoInterfaceDeclaration }
val isDataClass = Requirement("is a `data class`") { it.hasMod(KoModifier.DATA) }
val isEnum = Requirement("is an `enum class`") { it.hasMod(KoModifier.ENUM) }
val isSealed = Requirement("is `sealed`") { it.hasMod(KoModifier.SEALED) }
val isValueClass = Requirement("is a `value class`") { it.hasMod(KoModifier.VALUE) }
val isAbstract = Requirement("is `abstract`") { it.hasMod(KoModifier.ABSTRACT) }
val isInternal = Requirement("is `internal`") { it.hasMod(KoModifier.INTERNAL) }
val isFunInterface = Requirement("is a `fun interface`") { it is KoInterfaceDeclaration && it.hasMod(KoModifier.FUN) }

/** Has any annotation at all; `isAnnotatedWith(name)` narrows to a specific one. */
val isAnnotated = Requirement("has an annotation") { (it as? KoAnnotationProvider)?.annotations?.isNotEmpty() == true }

/** Declared in a file whose name (minus `.kt`) matches the declaration's name — the "own file" rule. */
val hasFileNameMatchingDeclaration = Requirement("is declared in a file matching its name") { d ->
    val name = (d as? KoNameProvider)?.name
    val file = (d as? KoContainingFileProvider)?.containingFile
    name != null && file != null && file.path.substringAfterLast("/").removeSuffix(".kt") == name
}

fun isInPackage(glob: String) = Requirement("resides in `$glob`") { it.residesIn(glob) }
fun isAnnotatedWith(name: String) =
    Requirement("is annotated `@$name`") { (it as? KoAnnotationProvider)?.hasAnnotationWithName(name) == true }

fun hasNameEndingWith(suffix: String) =
    Requirement("is named `[Name]$suffix`") { (it as? KoNameProvider)?.name?.endsWith(suffix) == true }

fun extends(parentName: String) =
    Requirement("extends `$parentName`") { d ->
        (d as? KoParentProvider)?.parents()?.any { it.name == parentName } == true
    }

fun oneOf(vararg options: Requirement) =
    Requirement("satisfies one of: {${options.joinToString(", ") { it.description }}}") { d -> options.any { it.matches(d) } }

fun not(requirement: Requirement) =
    Requirement("does not satisfy: ${requirement.description}") { !requirement.matches(it) }

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
