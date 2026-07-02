package architecture.rules.data

import architecture.registry.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment

@Describe("""
    A client-side interface declared in `..data..` (but not `data.storage`) that is **not** a
    Repository — typically the contract for a low-level concern with platform-specific actuals
    (e.g., `BinaryUploadClient` for chunked file upload).

    * **Note**: These exist to give Repositories a clean abstraction over a concrete platform
      capability. If you find yourself writing one, ask whether it belongs in `:platform:client`
      instead — feature-local data abstractions are appropriate when the contract is
      feature-specific.
""")
object ClientDataInterface : Construct<DataLayer>(
    requirements = listOf(
        isInterface,
        predicate("resides in `feature.[name].data` (not `data.storage`)") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && !pkg.containsPackageSegment("storage")
        },
    ),
)
