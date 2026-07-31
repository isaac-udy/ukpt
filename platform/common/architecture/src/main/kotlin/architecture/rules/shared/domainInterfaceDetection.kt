package architecture.rules.shared

import architecture.definitions.containingFilePackage
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
