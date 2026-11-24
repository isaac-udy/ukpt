package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A top-level function or property in test sources that builds test data or performs a shared
    test step, such as a builder for a domain model used by several tests. Scenarios, Inspections,
    Test Graphs and Host Edges are their own Constructs.
""")
object Fixture : Construct<Testing>(
    testedBy = TestExpectation.none("test infrastructure"),
    requirements = listOf(
        oneOf(isFunction, isProperty),
        not(isAnnotatedWith("Composable")),
        not(hasScenarioReceiver),
        not(hasServerInspectionReceiver),
        not(hasClientInspectionReceiver),
        not(isSubcutaneousGraph),
        not(isHostEdges),
    ),
)
