package architecture.rules

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import architecture.definitions.featureName
import architecture.definitions.isFeatureModule
import architecture.definitions.isServerModule
import architecture.registry.Violation
import architecture.registry.rules
import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

/**
 * The feature top-level / DI layer (§3.5, §4.5) — the single, self-contained definition of every
 * `R-FEAT` rule. The top-level `feature.[name]` package is reserved for DI wiring; concrete classes
 * (ServiceImpls, helpers) live in their layer-specific package.
 *
 * A construct owns both its **requirements** (what it means to *be* the construct — classification)
 * and its **rules** (what the construct must *do* — functionality). The DI-binding-style rule, which
 * scans every file in the layer rather than a single construct's population, lives at the layer level.
 *
 * Declared WITHOUT `inPackage`: the feature layer is the top-level `feature.[name]` package *minus*
 * the four axis sub-packages (`data`/`domain`/`services`/`ui`), which the registry's single-glob
 * `inPackage` (no exclude support) cannot express — so there is no exhaustiveness rule, and each
 * construct carries its own package boundary instead.
 *
 * Rule ids are the `by val` path, e.g. `featureRules.dependencyModule.ownFeatureBindingsOnly`.
 */
val featureRules by rules {

    // ---- §4.5.1 Dependency modules -----------------------------------------------------------
    val dependencyModule by construct {
        // what it is
        val inTopLevelFeaturePackage by requirement("DI modules must be defined in the top-level `feature.[name]` package of the `:client` and `:server` modules") {
            any { decl ->
                decl.isFeatureModule() &&
                    decl.containingFilePackage().let { pkg ->
                        pkg.startsWith("feature.") &&
                            !pkg.containsPackageSegment("data") &&
                            !pkg.containsPackageSegment("domain") &&
                            !pkg.containsPackageSegment("services") &&
                            !pkg.containsPackageSegment("ui")
                    }
            }
        }
        val koinValModule by requirement("DI modules are Koin `val` modules whose names end in `Dependencies`") {
            isProperty().and(hasNameEndingWith("Dependencies"))
        }

        // what it must do
        val ownFeatureBindingsOnly by rule("The DI module for a feature must only bind/provide dependencies that are both defined and implemented in that feature") {
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

        val urpcServiceBinding by rule("Register a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block") {
            note("`bindService` (from `dev.isaacudy.udytils.urpc.koin`) registers the binding under its own concrete type, bound to `UrpcService`, with the impl resolved lazily.")
            note("Do NOT use `scoped<UrpcService> { [Name]ServiceUrpcBinding { get() } }` — every such binding shares the `UrpcService` definition key, so co-registered services override each other and `getAll<UrpcService>()` returns only one.")
            note("`urpcService(::[Name]ServiceUrpcBinding)` is the equivalent standalone form when there is no impl definition to chain off.")
            guidance()
        }
    }

    // ---- §4.5.1 DI registration helper (unnumbered construct) --------------------------------
    val dependencyModuleHelper by construct {
        val declaredAsFunction by requirement("A DI registration helper is a function") {
            isFunction()
        }
        val internalVisibility by requirement("A DI registration helper is `internal` visibility") {
            hasModifier(KoModifier.INTERNAL)
        }
        val moduleReceiver by requirement("A DI registration helper has a Koin `Module` receiver") {
            function { declaration -> declaration.receiverType?.name == "Module" }
        }
    }

    // ---- §4.4 Service implementations (server) -----------------------------------------------
    val serviceImplementation by construct {
        val declaredAsClass by requirement("A service implementation is a class") {
            isClass()
        }
        val serviceImplSuffix by requirement("A service implementation is named `[Name]ServiceImpl`") {
            hasNameEndingWith("ServiceImpl")
        }
        val inServerModule by requirement("A service implementation is defined in a `:server` module") {
            cls { it.isServerModule() }
        }
        val internalVisibility by requirement("A service implementation is `internal` visibility") {
            hasModifier(KoModifier.INTERNAL)
        }
        val implementsServiceInterface by requirement("A service implementation implements a `services` interface") {
            cls { declaration ->
                declaration.parents().any { Classifiers.serviceInterface.test(it) }
            }
        }
        val noDomainInterfaceInjection by requirement("A service implementation must not inject domain interfaces") {
            cls { declaration ->
                declaration.primaryConstructor?.parameters.orEmpty().none { param ->
                    val source = param.type.sourceDeclaration as? KoBaseDeclaration
                    source != null && Classifiers.domainInterface.test(source)
                }
            }
        }

        val noUiDependency by rule("Service implementations must not depend on the `ui` package") {
            rationale(
                """
                ServiceImpls run on the server and have no Compose runtime — a UI import here would
                either fail to compile in `:server` or mean a UI type has been pulled out of `ui` and
                is being treated as data, both of which are wrong (§4.4.2, §3.4.4). If you need a
                shared shape with the UI, put it in the feature's `:api` domain or services package.
                """.trimIndent(),
            )
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                if (cls.containingFile.imports.any { it.name.containsPackageSegment("ui") }) {
                    listOf(Violation(decl, "service implementation imports the `ui` package"))
                } else {
                    emptyList()
                }
            }
        }
    }

    // ---- §4.5.1 DI binding style (layer-level — not tied to one construct) --------------------
    val constructorReferenceBindings by rule("DI bindings must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`") {
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
