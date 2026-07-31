package architecture.rules.clientdata

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.isFeatureRootPackage
import architecture.definitions.isServerModule
import architecture.rules.shared.domainInterfaceNamesOnSide
import architecture.rules.shared.simpleTypeNames
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

@Describe("""
    `feature.[name].client.data` — Repository implementations and client-side local persistence
    (Keychain, SharedPreferences, etc.). The client's outer edge, and the mirror of
    [`server.data`](serverdata.md): a [Repository](#repository) provides
    [domain interfaces](clientdomain.md#domain-interface) for the rest of the client to consume, the
    way a [server Repository](serverdata.md#repository) provides
    [server domain interfaces](serverdomain.md#domain-interface). The two carry the same name because
    they are the same construct on opposite sides; what differs is what they read through — a
    [Service](serverservices.md#service-interface) and local storage here, a
    [StorageClass](serverdata.md#storage-class) over a table there.

    This is also the only layer that may talk to the server: Repositories call
    [Services](serverservices.md#service-interface) — the `:api` contract — to reach it
    (`ClientData.clientServerDependencyRestriction`).
""")
object ClientData : RuleGroup(
    inPackage = "feature..client.data..",
    constructs = listOf(
        Repository,
        ClientDataInterface,
        ClientDataImplementation,
        ClientStorage,
    ),
) {

    // §3.3 `client.data` package dependencies (layer-level — not tied to one construct)
    @Describe("The `client.data` layer must provide implementations of `client.domain` interfaces by exposing them as properties, not by inheriting them")
    val providesDomainImplementations by rule {
        note("A Repository that implements a domain interface, or fails to expose one as a `public val`, fails the enforcing rules directly.")
        enforcedBy(Repository.doesNotImplementDomainInterfaces, Repository.exposesDomainInterfacesAsProperties)
    }

    @Describe("A `client.data` class must not inject `domain` interfaces; logic that requires multiple domain interfaces belongs in a UseCase")
    val noInjectingDomainInterfaces by rule {
        rationale(
            """
            Repositories implement domain interfaces. If one injects a domain interface, it is calling
            a sibling Repository through the abstract layer, which makes the dependency graph unreadable
            and easy to cycle. Logic that needs multiple domain interfaces belongs in a UseCase.
            """.trimIndent(),
        )
        note("Matched by name against the client's classified domain interfaces, bare or inside a wrapper such as `Lazy<…>` — an `:api`-declared parameter type often resolves to no source declaration, so resolution-based matching would silently skip exactly the published contracts.")
        scope { scope, exempt ->
            val domainInterfaces = scope.domainInterfaceNamesOnSide("client")
            scope.classes()
                .filter { it.isFeatureModule() }
                .filter { it.resideInPackage("feature..client.data..") }
                .filterNot { exempt(it) }
                .flatMap { cls ->
                    cls.primaryConstructor?.parameters.orEmpty()
                        .filter { param -> param.type.name.simpleTypeNames().any { it in domainInterfaces } }
                        .map { param -> Violation(cls, "data class injects domain interface `${param.type.name}`") }
                }
        }
    }

    @Describe("A `client.data.storage` class must use `internal` visibility where the language allows (see `ClientData.ClientStorage.internalVisibility`)")
    val storageInternalVisibility by rule {
        enforcedBy(ClientStorage.internalVisibility)
    }

    @Describe("The `client.data` layer must not depend on the `ui` package")
    val noUiDeps by rule {
        rationale(
            """
            UI is the outermost layer; `client.data` sits beneath it and supplies the domain interfaces
            the UI consumes. If it imports a UI type the layering becomes circular and the Repository can no
            longer be tested without a Compose runtime.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isInClientData() }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import -> import.name.containsPackageSegment("ui") }
                }
                .map { Violation(it.path, "data file imports a ui package") }
        }
    }

    @Describe("`client.data` is the only client package that may import a `server.services` contract, and client code must not import any other server code")
    val clientServerDependencyRestriction by rule {
        rationale(
            """
            The network is the single connection between the two sides, and `client.data` is the
            layer that uses it. A ViewModel that imports a service contract has bypassed the
            abstraction that makes it testable and swappable; a `client.domain` file that does has
            stopped being pure.

            The contract only — never a ServiceImpl, never `server.data`, never `server.domain`.
            What the client may see is exactly what the server publishes to `:api` as its wire
            surface.
            """.trimIndent(),
        )
        note("The population is every feature file the client compiles — everything that is not server-private, meaning not in a `server.**` package and not on a `:server` module.")
        note("A feature root file on a `:client` module is the feature's DI module, whose job is to bind a urpc client and therefore to name the contract; roots are governed by `FeatureRules` and are out of scope here.")
        note("Tested over `feature.[name].server.**` imports: the contract is `feature.[name].server.services.**`, declared in `:api` so both sides see it, and everything else under `server.` is the server's own business.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isClientSideFile() }
                .filterNot { it.isFeatureRootPackage() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    val mayReachTheContract = file.isInClientData()
                    file.imports
                        .filter { it.name.startsWith("feature.") && it.name.contains(".server.") }
                        .filterNot { mayReachTheContract && it.name.contains(".server.services.") }
                        .map {
                            Violation(
                                file.path,
                                if (mayReachTheContract) {
                                    "client.data imports server code `${it.name}` — the `server.services` contract is the only thing it may reach"
                                } else {
                                    "`${file.packagee?.name}` imports server code `${it.name}` — only `client.data` may import the server contract"
                                },
                            )
                        }
                }
        }
    }

    @Describe("A `client.data` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root")
    val subsystemVisibility by rule {
        note("`client.data.storage` is the exception and is visible from anywhere in this layer: it is the local-persistence half of the layer rather than a subsystem.")
        enforcedBy("ProjectRules.subsystemVisibility")
    }

    @Describe("A `client.data` subsystem package imports `client.domain` only through its mirror subsystem, that subsystem's direct children, and their ancestors")
    val subsystemMirrorsDomain by rule {
        note("A [Repository](#repository) at the layer root provides root-declared contracts, unconstrained by the mirror; a mirrored `client.data.[sub]` package provides that subsystem's.")
        enforcedBy("ProjectRules.subsystemMirrorsDomain")
    }
}

/**
 * True for a feature file the client compiles: everything that is not server-private — not in a
 * `server.**` package, and not on a `:server` module. Feature roots, the `:api` vocabulary, and
 * every `:client` file are all client-visible in this sense.
 */
internal fun KoFileDeclaration.isClientSideFile(): Boolean {
    val pkg = packagee?.name ?: return false
    if (!pkg.startsWith("feature.")) return false
    if (pkg.contains(".server.")) return false
    return !isServerModule()
}

/** True for a file in `feature.[name].client.data` — the file-level form of the group's gate. */
internal fun KoFileDeclaration.isInClientData(): Boolean {
    val pkg = packagee?.name ?: return false
    return pkg.contains(".client.data")
}
