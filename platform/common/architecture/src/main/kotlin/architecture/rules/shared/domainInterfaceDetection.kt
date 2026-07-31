package architecture.rules.shared

import architecture.definitions.containingFilePackage
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration

/**
 * True if [declaration] (or, for a parent reference, its source declaration) is a domain interface
 * declared in the given side's `domain` package. Re-expresses the domain-interface classification
 * (see [DomainInterfaceRules]' requirements) plus side-scoped residence, so data-layer rules can
 * test a resolved type without reaching into another group's construct.
 */
internal fun isDomainInterfaceOnSide(declaration: KoBaseDeclaration?, side: String): Boolean {
    val source = when (declaration) {
        is KoParentDeclaration -> declaration.sourceDeclaration as? KoBaseDeclaration
        else -> declaration
    }
    val iface = source as? KoInterfaceDeclaration ?: return false
    val pkg = iface.containingFilePackage()
    if (!pkg.contains(".$side.domain.") && !pkg.endsWith(".$side.domain")) return false
    if (!iface.hasFunModifier || iface.hasSealedModifier) return false
    val hasOperatorInvoke = iface.functions().any { it.name == "invoke" && it.hasOperatorModifier }
    if (!hasOperatorInvoke) return false
    val abstractFunctionsSuspendOrFlow = iface.functions()
        .filter { it.name == "invoke" || !it.text.contains("=") }
        .all { it.hasSuspendModifier || it.returnType?.name?.contains("Flow") == true }
    if (!abstractFunctionsSuspendOrFlow) return false
    val hasFlowReturn = iface.functions()
        .any { it.name == "invoke" && it.returnType?.name?.contains("Flow") == true }
    return !hasFlowReturn || iface.name.startsWith("FlowOf")
}

/**
 * The simple names of every domain interface classified on [side], for name-based matching of
 * parent references and constructor-parameter types. Name-based rather than resolution-based
 * deliberately: an `:api`-declared interface often resolves to no source declaration from its use
 * site, so resolution-based matching silently skips exactly the published contracts.
 */
internal fun KoScope.domainInterfaceNamesOnSide(side: String): Set<String> =
    interfaces()
        .filter { isDomainInterfaceOnSide(it, side) }
        .map { it.name }
        .toSet()

/**
 * Every simple type name a type expression mentions, so a wrapped `Lazy<SomeInterface>` reads the
 * same as a bare `SomeInterface`.
 */
internal fun String.simpleTypeNames(): List<String> =
    Regex("""[A-Za-z_][A-Za-z0-9_.]*""")
        .findAll(this)
        .map { it.value.substringAfterLast('.') }
        .toList()
