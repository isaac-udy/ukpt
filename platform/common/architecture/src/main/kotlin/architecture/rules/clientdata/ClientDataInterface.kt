package architecture.rules.clientdata

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment

@Describe("""
    A client-side interface declared in `feature.[name].client.data` (but not `client.data.storage`)
    that is not a Repository. Typically the contract for a low-level concern with platform-specific actuals,
    such as `BinaryUploadClient` for chunked file upload.

    * **Note:** These exist to give Repositories a clean abstraction over a concrete platform
      capability. If you find yourself writing one, ask whether it belongs in `:platform:client`
      instead; a feature-local data abstraction is appropriate when the contract is
      feature-specific.
""")
object ClientDataInterface : Construct<ClientData>(
    requirements = listOf(
        isInterface,
        predicate("resides in `feature.[name].client.data` (not `client.data.storage`)") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && !pkg.containsPackageSegment("storage")
        },
    ),
)
