package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    Abstractions used inside a subsystem (e.g. a strategy contract whose implementations live
    in the same subpackage).
""")
object InternalInterface : Construct<ServicesLayer>(
    requirements = listOf(
        isInterface,
        predicate("resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    ),
)
