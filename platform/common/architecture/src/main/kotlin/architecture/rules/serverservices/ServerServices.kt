package architecture.rules.serverservices

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containingFilePackage
import architecture.definitions.featureName
import architecture.definitions.featureNameFromContainingPackage
import architecture.definitions.isFeatureModule
import architecture.definitions.resolveTypeToken
import architecture.definitions.typeTokens
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

@Describe("""
    `feature.[name].server.services` defines the contract between client and server, and the
    server's entry points. The contract lives in `:api`, so both sides see it; the implementation
    lives in `:server` under the same package name.

    Everything else in the layer is an **entry point** — a class something outside the process
    triggers. The template ships one kind: a [ServiceImpl](#service-impl) answering a network
    request. The work an entry point triggers is not declared here — it is stated as
    [`server.domain` interfaces](serverdomain.md#domain-interface) and done by
    [UseCases](serverdomain.md#use-case).

    On the client, [Repositories](clientdata.md#repository) (in `client.data`) inject Service
    contracts to call the server. On the server, an entry point composes domain interfaces;
    persistence sits behind them, in [`server.data`](serverdata.md), which this layer never imports.

    Client/server communication uses **urpc** (`dev.isaacudy.udytils:urpc-*`): a service is an
    `@Urpc` interface, and KSP generates the client, the server binding, and the wire descriptors.
    See [Service Interface](#service-interface).

    ## Cross-layer dependencies

    Within a feature, the layer dependency rules are:

    * Each side's `domain` imports feature roots only. It is the middle of its hexagon.
    * `server.services` may depend on [`server.domain`](serverdomain.md) — and never on
      [`server.data`](serverdata.md) (`ServerServices.noDataImports`).
    * `server.data` may depend on `server.domain` — and never on `server.services`
      (`ServerData.noServiceImports`).
    * `client.data` may depend on `client.domain` and on this layer's contracts, so Repositories
      can call the server (`ClientData.clientServerDependencyRestriction`).
    * `client.ui` may depend on `client.domain` only; server calls go through
      [Repositories](clientdata.md#repository), which provide
      [domain interfaces](clientdomain.md#domain-interface) for the UI to consume.
    * Nothing depends on `client.ui`.

    Reading these as a directed graph:

    * On the client: `client.ui → client.domain ← client.data`.
    * On the server: `server.services → server.domain ← server.data`.

    The two sides meet only at the contract this layer declares in `:api`. Cross-feature use of
    another feature's services goes through `:api` as well: `ServerServices.crossFeatureViaApi`,
    in the [rules](#rules) below.

    On the server, that contract is a door, not a composition mechanism: a class in this layer
    never injects another feature's Service contract
    (`ServerServices.noForeignServiceContractInjection`). One server feature reaches another
    through the capability the owner publishes — a
    [`server.domain` interface](serverdomain.md#domain-interface) whose file resides in `:api` —
    which is the same channel the domain layers use.

    ## Sub-packages

    Any sub-package of the layer is an ordinary subsystem under
    `ProjectRules.subsystemVisibility`: it sees its own package, its direct children, and its
    ancestors up to the layer root — never a sibling.

    ## Persistence

    The server's persistence layer is [`server.data`](serverdata.md). Entry points reach it only
    through [`server.domain` interfaces](serverdomain.md#domain-interface), which a
    [Repository](serverdata.md#repository) provides (`ServerServices.noDataImports`); the Postgres
    conventions, codegen pipeline, and reactive flows are documented on that layer's page.
""")
object ServerServices : RuleGroup(
    inPackage = "feature..server.services..",
    constructs = listOf(
        ServiceInterface,
        ServiceImpl,
    ),
) {

    // ---- cross-layer dependency rules (layer-level — not tied to one construct) ---------------
    @Describe("The `server.services` layer must never import `server.data`")
    val noDataImports by rule {
        rationale(
            """
            This is the hexagon. `server.domain` sits between services and persistence and knows
            neither: services consume domain interfaces, and Repositories provide them. A
            ServiceImpl that reaches a table directly has skipped the layer where the contract
            should have been stated, so nothing else can reuse that access, and nothing names what
            the service actually needed.

            `ServerData.noServiceImports` is the other half. Together they make storage a thing that
            *satisfies* a stated need rather than a thing services reach through.
            """.trimIndent(),
        )
        note("Tested over imports of persistence, wherever the imported file sits: reaching a table is the same act whatever the package holding it is called.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isInServerServices() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("feature.") }
                        .filter { it.name.contains(".server.data.") || it.name.contains(".services.storage.") }
                        .map { Violation(file.path, "server.services imports persistence `${it.name}` — state a `server.domain` interface instead") }
                }
        }
    }

    @Describe("The `server.services` layer must not import client code")
    val noClientImports by rule {
        rationale("The two sides meet at the RPC contract and nowhere else.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isInServerServices() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("feature.") && it.name.contains(".client.") }
                        .map { Violation(file.path, "server.services imports client code `${it.name}`") }
                }
        }
    }

    @Describe("The `services` layer may depend on another feature's `services` only via that feature's `:api` module")
    val crossFeatureViaApi by rule {
        enforcedBy("ModuleRules.clientApiOnly", "ModuleRules.serverApiOnly", "ModuleRules.crossFeatureCodeViaApi")
    }

    @Describe("A class in `server.services` must not inject another feature's Service contract")
    val noForeignServiceContractInjection by rule {
        rationale(
            """
            A Service contract is the door between a client and this server, not a way for one
            server feature to call another. Injecting another feature's contract gives a
            server-internal call the wire's shape — a request object, a session the caller must
            already hold, an error type written for a screen — and hands the caller every
            operation on that service when it needed one capability. That capability is what the
            owning feature's [`server.domain` interfaces](serverdomain.md#domain-interface) say:
            the owner publishes the narrow one to `:api`, a Repository provides it, and the caller
            states it like any other contract.
            """.trimIndent(),
        )
        note("Tested on the primary constructor of every class in `feature.[name].server.services` and its sub-packages: a parameter whose type — bare, or inside a wrapper such as `Lazy<…>` — is an `@Urpc` interface belonging to another feature.")
        note("A feature's own contract is unaffected: a class in this layer may wrap or delegate to its own feature's Service.")
        scope { scope, exempt ->
            // Keyed by fully-qualified name: a parameter reference is resolved through its file's
            // imports (alias-aware) before lookup, so a simple name shared by two features'
            // contracts, an aliased import, and a fully-qualified reference all land on the
            // contract actually named.
            val contractOwnerByFqn: Map<String, String> = scope.interfaces()
                .filter { iface -> iface.annotations.any { it.name == "Urpc" } }
                .mapNotNull { iface ->
                    val fqn = (iface as? KoFullyQualifiedNameProvider)?.fullyQualifiedName ?: return@mapNotNull null
                    fqn to iface.featureName()
                }
                .toMap()
            scope.classes()
                .filter { it.isFeatureModule() }
                .filter { servicesPackageRegex.matches(it.containingFilePackage()) }
                .filterNot { exempt(it) }
                .flatMap { cls ->
                    val ownFeature = cls.featureNameFromContainingPackage()
                    cls.primaryConstructor?.parameters.orEmpty()
                        .flatMap { param -> typeTokens(param.type.name).map { param to it } }
                        .mapNotNull { (param, token) ->
                            val fqn = cls.containingFile.resolveTypeToken(token) ?: return@mapNotNull null
                            val owner = contractOwnerByFqn[fqn] ?: return@mapNotNull null
                            if (owner == ownFeature) return@mapNotNull null
                            Violation(
                                cls,
                                "injects `$token`, the `$owner` feature's Service contract, as `${param.name}` — " +
                                    "state a `server.domain` interface that feature publishes to `:api` instead",
                            )
                        }
                }
        }
    }

    @Describe("A `server.services` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root")
    val subsystemVisibility by rule {
        enforcedBy("ProjectRules.subsystemVisibility")
    }

    @Describe("A `server.services` subsystem package imports `server.domain` only through its mirror subsystem, that subsystem's direct children, and their ancestors")
    val subsystemMirrorsDomain by rule {
        note("A file at the layer root — a ServiceImpl — is unconstrained by the mirror and sees the whole of `server.domain`.")
        enforcedBy("ProjectRules.subsystemMirrorsDomain")
    }
}

/**
 * The services package, `feature.x.server.services.**`. Group 1 is the feature name, group 2 the
 * dotted sub-path after `services` (absent for the services package itself).
 */
internal val servicesPackageRegex = Regex("""^feature\.([^.]+)\.server\.services(?:\.(.+))?$""")

/**
 * The dotted package sub-path after `…server.services` for this declaration, or `null` if it isn't
 * in a services package. `""` for the services package itself; `"tools"` etc. for the
 * sub-packages. Guards against false matches like `…servicesRegistry`.
 */
private fun KoBaseDeclaration.servicesSubpath(): String? =
    servicesPackageRegex.matchEntire(containingFilePackage())?.groupValues?.get(2)

/**
 * In `feature.[name].server.services` itself (the contract, the ServiceImpl) — no further
 * segments. A declaration in a sub-package the architecture does not name classifies as
 * nothing, so the exhaustiveness rules report it.
 */
internal fun KoBaseDeclaration.isInServicesRoot(): Boolean = servicesSubpath() == ""

/** True for a file in `feature.[name].server.services.**` — the file-level form of the group's gate. */
internal fun KoFileDeclaration.isInServerServices(): Boolean {
    val pkg = packagee?.name ?: return false
    return pkg.contains(".server.services")
}
