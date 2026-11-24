package architecture.rules.shared

import architecture.definitions.TestCategories
import dev.isaacudy.udytils.architecture.*

/** The side-private exception shape, shared by both sided domain groups. No rules — the shape is the whole of it. */
abstract class DomainExceptionRules<G : RuleGroup> : Construct<G>(
    testedBy = TestExpectation.anyOf(TestCategories.Subcutaneous, TestCategories.Unit),
    requirements = listOf(
        hasNameEndingWith("Exception"),
        isClassWhere("is a class extending RuntimeException/Exception") { decl ->
            decl.parents().any { it.name == "RuntimeException" || it.name == "Exception" }
        },
    ),
)
