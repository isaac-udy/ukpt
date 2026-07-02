package architecture.rules.feature

import architecture.registry.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import architecture.definitions.featureName
import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

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
object DependencyModule : Construct<FeatureRules>(
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
