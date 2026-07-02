package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    An abstraction used inside a subsystem, such as a strategy contract whose implementations
    live in the same subpackage.
""")
object InternalInterface : Construct<ServicesLayer>(
    requirements = listOf(
        isInterface,
        predicate("resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    ),
)
