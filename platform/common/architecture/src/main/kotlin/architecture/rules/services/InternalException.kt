package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    An exception thrown only by internal helpers. Service-level exceptions belong on the
    `Service` interface (see [Service Interface](#service-interface)).
""")
object InternalException : Construct<ServicesLayer>(
    requirements = listOf(
        isClassWhere("is a class named `[Name]Exception`, thrown only by internal helpers") { it.name.endsWith("Exception") },
        predicate("resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    ),
)
