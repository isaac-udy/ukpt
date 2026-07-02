package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    Payloads that flow from one subsystem through the orchestrator into another. A carrier
    lives at the bare `services.internal` ancestor so both producer and consumer can name it
    under the data-shape carve-out (see
    [hierarchical visibility](#hierarchical-visibility-within-servicesinternal)).
""")
object InternalDataCarrier : Construct<ServicesLayer>(
    requirements = listOf(
        isClassWhere("is a `data class` payload that flows between subsystems through the orchestrator") { it.hasDataModifier },
        predicate("resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    ),
)
