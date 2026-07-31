package architecture.rules.shared

import architecture.utils.isDomainCompatibleType
import dev.isaacudy.udytils.architecture.*

/** The domain extension-function shape, shared by both sided domain groups. No rules — the shape is the whole of it. */
abstract class ExtensionFunctionRules<G : RuleGroup> : Construct<G>(
    requirements = listOf(
        isFunctionWhere("declares an explicit extension receiver") { it.receiverType != null },
        isFunctionWhere("has receiver/return/parameter types that are domain models, primitives, or collections of those") { decl ->
            val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val returnOk = decl.returnType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val parametersOk = decl.parameters.all { isDomainCompatibleType(it.type.name, decl.containingFile) }
            receiverOk && returnOk && parametersOk
        },
    ),
)
