package architecture.rules.services

import architecture.registry.*

@Describe("""
    Exceptions thrown only by internal helpers; service-level exceptions belong on the
    `Service` interface (see [Services](#service-interface)).
""")
object InternalException : Construct<ServicesLayer>(
    requirements = listOf(
        isClassWhere("is a class named `[Name]Exception`, thrown only by internal helpers") { it.name.endsWith("Exception") },
        predicate("resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    ),
)
