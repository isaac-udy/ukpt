package architecture.rules.feature

import architecture.registry.*

import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

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
object FeatureRules : RuleGroup(
    constructs = listOf(
        DependencyModule,
        DependencyModuleHelper,
    ),
) {

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
