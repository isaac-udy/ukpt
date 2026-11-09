package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A top-level function or property in test sources that builds test data or performs a shared
    test step, such as a builder for a domain model used by several tests. Scenarios and Test
    Graphs are their own Constructs.
""")
object Fixture : Construct<Testing>(
    requirements = listOf(
        oneOf(isFunction, isProperty),
        not(isAnnotatedWith("Composable")),
        not(hasScenarioReceiver),
        not(isSubcutaneousGraph),
    ),
)
