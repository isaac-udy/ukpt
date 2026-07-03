package architecture.rules.data

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment

@Describe("""
    A client-side class in `..data..` (but not `data.storage`) that is not a Repository. Usually
    a platform-specific implementation of a [client data interface](#client-data-interface).
""")
object ClientDataImplementation : Construct<DataLayer>(
    requirements = listOf(
        isClass,
        isClassWhere("is not named `[Name]Repository`") { !it.name.endsWith("Repository") },
        predicate("resides in `feature.[name].data` (not `data.storage`)") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && !pkg.containsPackageSegment("storage")
        },
    ),
)
