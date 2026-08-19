package architecture.rules.feature

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containsPackageSegment
import architecture.definitions.isApiModule
import architecture.definitions.isFeatureModule
import architecture.definitions.isFeatureRootPackage
import architecture.utils.isPlatformSpecificImport
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/*
 * Declared WITHOUT `inPackage`: this group governs the top-level `feature.[name]` package *minus*
 * the layer sub-packages (anything with a `data`/`domain`/`services`/`ui` segment), and neither the
 * two-segment package nor the exclusion is expressible as the registry's single-glob `inPackage`
 * (no exclude support) — so there is no exhaustiveness rule, and each Construct carries its own
 * package and module boundary instead. ServiceImpls are classified by the services layer
 * (`ServerServices.ServiceImpl`), so there is no ServiceImpl construct here — that would
 * double-classify every ServiceImpl and break the global layer-membership check.
 */
@Describe("""
    A feature has a client implementation and a server implementation that communicate only through
    the RPC contract, and share one vocabulary:

    ```
    client.ui → client.domain ← client.data → [ contract ] ← server.services → server.domain ← server.data
    ```

    The client and server have the same layer structure: a [UseCase](clientdomain.md#use-case) over
    [domain interfaces](clientdomain.md#domain-interface) answered by a
    [Repository](clientdata.md#repository) on the client has the same shape as a
    [UseCase](serverdomain.md#use-case) over [domain interfaces](serverdomain.md#domain-interface)
    answered by a [Repository](serverdata.md#repository) on the server. Neither imports the other:
    the network is the only connection between client and server, and `:api` is the only channel
    between features.

    The feature **root** is `feature.[name]`, with no `client` or `server` segment. The Gradle
    module it is compiled into decides what it holds: in `:api` it is the feature's shared
    vocabulary; in `:client` and `:server` the same package name holds DI wiring.

    **In `:api`, the root holds the shared vocabulary**: the domain models, exceptions, and
    constants that both the client and the server use. It is common Kotlin that compiles for every
    target.

    The root also shows, straight off the package path, how far a change reaches. A
    [shared domain model](#shared-domain-model) is used by the client, the server, and potentially
    other features, so renaming a field is a compatibility event. A
    [domain model](clientdomain.md#domain-model) in
    [`client.domain`](clientdomain.md#domain-model) or
    [`server.domain`](serverdomain.md#domain-model) is used only within the client or server that
    defines it, and refactors freely. The `domain` layers build on the root: their models compose
    the shared ones.

    > **A change here is a compatibility event.** These types are serialized across the network, so
    > renaming a field, changing a type, or moving a sealed variant breaks compatibility across
    > features. A PR touching a feature root should be reviewed as one.

    The root holds domain objects and validation only: no interfaces with behaviour, no use cases, no
    logic beyond validating the values it carries. Anything with behaviour belongs on a side —
    single-function contracts are [domain interfaces](clientdomain.md#domain-interface) in
    `client.domain` or [`server.domain`](serverdomain.md#domain-interface).

    **In `:client` and `:server`, the same package holds the feature's wiring.** It is reserved for
    dependency injection: Koin modules that define the feature's DI bindings, wiring its
    [ViewModels](clientui.md#view-model), Repositories
    ([client](clientdata.md#repository), [server](serverdata.md#repository)),
    [UseCases](clientdomain.md#use-case) ([server](serverdomain.md#use-case)),
    [StorageClasses](serverdata.md#storage-class), and
    [Service](serverservices.md#service-interface) implementations into the graph. Concrete classes
    (ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.
""")
object FeatureRules : RuleGroup(
    constructs = listOf(
        SharedDomainModel,
        SharedException,
        SharedConstants,
        SharedExtensionFunction,
        SharedExtensionProperty,
        DependencyModule,
        DependencyModuleHelper,
    ),
) {

    @Describe("A feature root may import feature roots only, never a declaration from inside a side")
    val noSideImports by rule {
        rationale(
            """
            The root is shared vocabulary: both the client and server depend on it, so it can depend on neither.
            An import of `client.**` would make the type unusable on the server, and an import of
            `server.**` would drag persistence or wire machinery into a type the client compiles.
            """.trimIndent(),
        )
        note("Other features' roots are importable — real vocabularies reference each other. Keep that graph acyclic.")
        note("Tested as an allow-list: a `feature.` import is a root import when everything between the feature name and the imported declaration is a type name, so anything sitting in a deeper package is client- or server-private whatever that package is called.")
        note("Scoped to `:api`, where the vocabulary lives; the same package name on `:client`/`:server` holds the feature's DI module, whose job is to name both client and server implementations.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isFeatureRootPackage() && it.isApiModule() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("feature.") }
                        .filterNot { import -> import.name.namesFeatureRootDeclaration() }
                        .map { Violation(file.path, "feature root imports client- or server-private code `${it.name}`") }
                }
        }
    }

    @Describe("A feature root must not contain platform-specific dependencies, such as Android, Compose, Ktor, or SQL")
    val noPlatformDeps by rule {
        rationale(
            """
            The root is common Kotlin consumed by every target and by the server. A platform import
            here would break compilation for some target or drag transport/persistence machinery
            into the vocabulary itself.
            """.trimIndent(),
        )
        note("Scoped to `:api`, where the vocabulary lives; the same package name on `:client`/`:server` holds the feature's [dependency module](#dependency-module), which is out of scope because wiring a client or server necessarily names its platform types.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isFeatureRootPackage() && it.isApiModule() }
                .filterNot { exempt(it) }
                .filter { file -> file.imports.any { it.name.isPlatformSpecificImport() } }
                .map { Violation(it.path, "feature root file imports a platform-specific dependency") }
        }
    }

    @Describe("A feature root must declare only domain objects, constants, validation, and pure extensions over them")
    val modelsOnly by rule {
        rationale(
            """
            Behaviour in the root would be shared between client and server, and only vocabulary is
            shared. A single-function interface belongs in `client.domain` or `server.domain`
            ([client](clientdomain.md#domain-interface),
            [server](serverdomain.md#domain-interface)), where the data layer provides it.
            """.trimIndent(),
        )
        note("Enforced by the Constructs: a declaration in the root matching none of them fails the membership rule.")
        enforcedBy("architecture.everyDeclarationBelongsToALayer")
    }

    @Describe("A feature root type that participates in polymorphic serialization must pin an explicit `@SerialName`")
    val serialNamesPinned by rule {
        note("Enforced project-wide by `ProjectRules.serialNamePinnedOnPolymorphicTypes`; restated here because the root is where the wire vocabulary lives.")
        enforcedBy("ProjectRules.serialNamePinnedOnPolymorphicTypes")
    }

    @Describe("A DI binding must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`")
    val constructorReferenceBindings by rule {
        rationale(
            """
            The reference style lets Koin validate the constructor parameters against the graph at
            startup; the lambda style hides missing or cyclic dependencies until the first injection
            at runtime.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureTopLevelFile() }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.text.lines().any { line ->
                        Regex("""[,(]\s*get\s*[<(]""").containsMatchIn(line)
                    }
                }
                .map { Violation(it.path, "DI binding uses the `get()` lambda style instead of `singleOf(::Constructor).bind(...)`") }
        }
    }
}

/**
 * True for an import that names a declaration in a feature root — `feature.<name>.<Declaration>`,
 * plus any nested types under it (`feature.campaigns.Campaign.Id`). Everything between the feature
 * name and the imported declaration must be a type name, so a lowercase package segment anywhere in
 * that span means the declaration lives inside a side and this is not a root import.
 */
private fun String.namesFeatureRootDeclaration(): Boolean {
    if (!startsWith("feature.")) return false
    val afterFeature = removePrefix("feature.").split('.')
    if (afterFeature.size < 2) return false
    // The last segment is the imported declaration; the segments between it and the feature name are
    // its enclosing types when this is a root import, and its package when it is not.
    return afterFeature
        .drop(1)
        .dropLast(1)
        .all { it.firstOrNull()?.isUpperCase() == true }
}

/**
 * Reconstructs `FeatureLayer.inLayerPackage` (rootPackage `feature..` minus the `data`/`domain`/
 * `services`/`ui` layer sub-packages): a file in a feature module whose package is the top-level
 * `feature.[name]` package, not one of the layer packages.
 */
private fun KoFileDeclaration.isFeatureTopLevelFile(): Boolean {
    if (!isFeatureModule()) return false
    val pkg = packagee?.name.orEmpty()
    return pkg.startsWith("feature.") &&
        !pkg.containsPackageSegment("data") &&
        !pkg.containsPackageSegment("domain") &&
        !pkg.containsPackageSegment("services") &&
        !pkg.containsPackageSegment("ui")
}
