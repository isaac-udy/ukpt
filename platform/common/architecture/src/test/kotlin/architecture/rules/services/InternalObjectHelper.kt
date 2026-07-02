package architecture.rules.services

import architecture.registry.*

@Describe("`object`s holding pure helper functions.")
object InternalObjectHelper : Construct<ServicesLayer>(
    requirements = listOf(
        isObject,
        predicate("Resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    ),
)
