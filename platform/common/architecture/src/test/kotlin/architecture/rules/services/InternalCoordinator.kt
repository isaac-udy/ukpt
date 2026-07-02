package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    The orchestrators that compose subsystems (e.g. `SessionProcessingManager`) — see the
    [`services.internal` overview](#servicesinternal). Cross-subsystem composition belongs
    here, at bare `services.internal`, not to imports between sibling subsystems.
""")
object InternalCoordinator : Construct<ServicesLayer>(
    requirements = listOf(
        isClassWhere("is a concrete (non-`abstract`, non-`data`) class that is not a `Job` or `Exception`") { decl ->
            !decl.hasAbstractModifier &&
                !decl.hasDataModifier &&
                !decl.name.endsWith("Job") &&
                !decl.name.endsWith("Exception")
        },
        predicate("resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    ),
)
