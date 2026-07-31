package architecture.rules.shared

import architecture.definitions.isMutable
import dev.isaacudy.udytils.architecture.*

/** The side-private constants-object shape, shared by both sided domain groups. No rules — the shape is the whole of it. */
abstract class ConstantsRules<G : RuleGroup> : Construct<G>(
    requirements = listOf(
        isObjectWhere("is an `object` with only `val` properties and no functions") { decl ->
            decl.functions().isEmpty() && decl.properties().all { it.isVal && !it.isMutable() }
        },
        not(hasNameEndingWith("Workflow")),
    ),
)
