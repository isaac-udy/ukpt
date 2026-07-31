package architecture.rules.clientdata

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment

@Describe("""
    A client-side class in `feature.[name].client.data` (but not `client.data.storage`) that is not
    a Repository. Usually a platform-specific implementation of a
    [client data interface](#client-data-interface).
""")
object ClientDataImplementation : Construct<ClientData>(
    requirements = listOf(
        isClass,
        isClassWhere("is not named `[Name]Repository`") { !it.name.endsWith("Repository") },
        predicate("resides in `feature.[name].client.data` (not `client.data.storage`)") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && !pkg.containsPackageSegment("storage")
        },
    ),
)
