package architecture.rules.shared

import architecture.utils.isDomainCompatibleType
import dev.isaacudy.udytils.architecture.*

/** The domain extension-property shape, shared by both sided domain groups. No rules — the shape is the whole of it. */
abstract class ExtensionPropertyRules<G : RuleGroup> : Construct<G>(
    requirements = listOf(
        isPropertyWhere("declares an explicit extension receiver") { it.receiverType != null },
        isPropertyWhere("has a receiver/type that is a domain model, primitive, or collection of those") { decl ->
            val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val typeOk = decl.type?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            receiverOk && typeOk
        },
    ),
)
