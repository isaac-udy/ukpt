package architecture.rules.feature

import architecture.registry.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import architecture.definitions.featureName
import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

/*
 * Declared WITHOUT `inPackage`: the feature layer is the top-level `feature.[name]` package *minus*
 * the four axis sub-packages (`data`/`domain`/`services`/`ui`), which the registry's single-glob
 * `inPackage` (no exclude support) cannot express — so there is no exhaustiveness rule, and each
 * construct carries its own package boundary instead. ServiceImpls are classified by the `services`
 * axis (`ServicesLayer.ServiceImpl`), so there is no ServiceImpl construct here — that would
 * double-classify every ServiceImpl and break the global layer-membership check.
 */
@Describe("""
    The top-level `feature.[name]` package (in `:client` and `:server`) is reserved for
    dependency-injection wiring: Koin modules that define the feature's DI bindings, wiring its
    [ViewModels](ui.md#view-model), [Repositories](data.md#repository), [UseCases](domain.md#use-case),
    and [Service](services.md#service-interface) implementations into the graph. Concrete classes
    (ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.
""")
object FeatureRules : RuleGroup() {

    @Describe("""
        The configuration for Dependency Injection (DI) that wires the feature together.

        * **Note**: The naming convention is `[name]ClientDependencies` in `:client` and
          `[name]ServerDependencies` in `:server` — the construct enforces the `Dependencies` suffix;
          the `Client`/`Server` infix is convention.
        * **Note**: It is the responsibility of `:app` level modules (application shells) to collect
          all of the DI modules provided by feature modules and create the final dependency graph.
          When a new dependency module is added, it must be registered in both `:app:client:shared`
          and `:app:server`; when a new Service is added, it must be registered in `:app:server`.
    """)
    object DependencyModule : Construct(
        requirements = listOf(
            predicate("DI modules must be defined in the top-level `feature.[name]` package of the `:client` and `:server` modules") { decl ->
                decl.isFeatureModule() &&
                    decl.containingFilePackage().let { pkg ->
                        pkg.startsWith("feature.") &&
                            !pkg.containsPackageSegment("data") &&
                            !pkg.containsPackageSegment("domain") &&
                            !pkg.containsPackageSegment("services") &&
                            !pkg.containsPackageSegment("ui")
                    }
            },
            isProperty,
            hasNameEndingWith("Dependencies"),
        ),
    ) {
        // what it must do
        @Describe("The DI module for a feature must only bind/provide dependencies that are both defined and implemented in that feature")
        val ownFeatureBindingsOnly by rule {
            rationale(
                """
                If feature A binds an implementation of feature B's domain interface, feature B's DI
                graph silently depends on feature A — and removing/refactoring A breaks B's wiring at
                runtime, not at compile time. Each feature owns its own bindings; cross-feature
                consumption goes through `:api` interfaces only.
                """.trimIndent(),
            )
            constrain { decl, _ ->
                val property = decl as? KoPropertyDeclaration ?: return@constrain emptyList()
                val file = property.containingFile
                val owningFeature = file.featureName()
                file.imports
                    .filter { import -> import.name.startsWith("feature.") }
                    .filter { import -> import.featureName() != owningFeature }
                    .map {
                        Violation(
                            decl,
                            "DI module for feature `$owningFeature` binds a dependency from another feature (imports `${it.name}`)",
                        )
                    }
            }
        }

        @Describe("Register a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block")
        val urpcServiceBinding by guidance {
            note("`bindService` (from `dev.isaacudy.udytils.urpc.koin`) registers the binding under its own concrete type, bound to `UrpcService`, with the impl resolved lazily.")
            note("Do NOT use `scoped<UrpcService> { [Name]ServiceUrpcBinding { get() } }` — every such binding shares the `UrpcService` definition key, so co-registered services override each other and `getAll<UrpcService>()` returns only one.")
            note("`urpcService(::[Name]ServiceUrpcBinding)` is the equivalent standalone form when there is no impl definition to chain off.")
        }
    }

    @Describe("""
        An `internal` function with a Koin `Module` receiver that a `Dependencies` module calls to
        register a group of bindings — used to split a large module into readable, named chunks.
    """)
    object DependencyModuleHelper : Construct(
        requirements = listOf(
            isFunction,
            isInternal,
            isFunctionWhere("A DI registration helper has a Koin `Module` receiver") { declaration -> declaration.receiverType?.name == "Module" },
        ),
    )

    @Describe("DI bindings must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`")
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
 * Reconstructs `FeatureLayer.inLayerPackage` (rootPackage `feature..` minus the `data`/`domain`/
 * `services`/`ui` axis sub-packages): a file in a feature module whose package is the top-level
 * `feature.[name]` package, not one of the four axis packages.
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
