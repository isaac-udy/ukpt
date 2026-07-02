package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("`object`s holding pure helper functions.")
object InternalObjectHelper : Construct<ServicesLayer>(
    requirements = listOf(
        isObject,
        predicate("resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    ),
)
