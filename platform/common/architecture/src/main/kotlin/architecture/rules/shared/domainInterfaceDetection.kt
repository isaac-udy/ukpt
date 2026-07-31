package architecture.rules.shared

import architecture.definitions.containingFilePackage
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider

/** True for the name of a reactive return type — `Flow` or `Flow<…>`, not any name containing the substring. */
internal fun isFlowTypeName(name: String?): Boolean =
    name != null && (name == "Flow" || name.startsWith("Flow<"))

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
        .all { it.hasSuspendModifier || isFlowTypeName(it.returnType?.name) }
    if (!abstractFunctionsSuspendOrFlow) return false
    val hasFlowReturn = iface.functions()
        .any { it.name == "invoke" && isFlowTypeName(it.returnType?.name) }
    return !hasFlowReturn || iface.name.startsWith("FlowOf")
}

/**
 * The fully-qualified names of every domain interface classified on [side]. Use-site matching
 * resolves a reference through its file's imports (see `resolveTypeToken`) and looks it up here:
 * FQN-based rather than resolution-based because an `:api`-declared interface often resolves to no
 * source declaration from its use site, and FQN-based rather than simple-name-based because an
 * unrelated vendor type sharing a project interface's simple name must not collide with it.
 */
internal fun KoScope.domainInterfaceFqnsOnSide(side: String): Set<String> =
    interfaces()
        .filter { isDomainInterfaceOnSide(it, side) }
        .mapNotNull { (it as? KoFullyQualifiedNameProvider)?.fullyQualifiedName }
        .toSet()
