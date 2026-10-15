package architecture.rules.feature

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.featureName
import architecture.definitions.isClientModule
import architecture.definitions.isFeatureModule
import architecture.definitions.isFeatureRootPackage
import architecture.definitions.isServerModule
import architecture.projectScope
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider

@Describe("""
    The configuration for Dependency Injection (DI) that wires the feature together.

    * **Note:** The naming convention is `[name]ClientDependencies` in `:client` and
      `[name]ServerDependencies` in `:server`. The Construct enforces the `Dependencies` suffix;
      the `Client`/`Server` infix is convention.
    * **Note:** The `:app` modules (application shells) are responsible for collecting the DI
      modules provided by feature modules into the final dependency graph. A `:client` dependency
      module is registered in every client shell's `ClientDependencies.kt` (`:app:client:common`
      and `:app:client:web-html`) and a `:server` one in `:app:server`; when a new Service is
      added, it must be registered in `:app:server`.
    * **Note:** Every application class is bound by constructor reference — `singleOf`,
      `factoryOf`, `scopedOf`, or `viewModelOf`, whichever lifetime the class needs — so the graph
      supplies every constructor parameter, and a registered class gives no parameter a default
      (`ProjectRules.constructorReferenceBindings`,
      `ProjectRules.injectableConstructorsHaveNoDefaults`). A lambda binds what the graph does not
      construct: a typed configuration object, a third-party client built through its builder, a
      Repository property under its domain interface.
    * **Note:** The `:app` shells keep their module lists in one place (`clientDependencies`,
      `serverDependencies`) so a graph-resolution test can verify every registered constructor
      against the graph without booting the application.
""")
object DependencyModule : Construct<FeatureRules>(
    requirements = listOf(
        predicate("resides in the top-level `feature.[name]` package of a `:client` or `:server` module") { decl ->
            // Exactly the feature root — a `Dependencies` property anywhere deeper would let an
            // undeclared layer package slip past the taxonomy by holding only DI code — and on a
            // side module: DI wiring binds implementations, which `:api` never holds.
            decl.isFeatureModule() && decl.isFeatureRootPackage() &&
                (decl.isClientModule() || decl.isServerModule())
        },
        isProperty,
        hasNameEndingWith("Dependencies"),
    ),
) {
    @Describe("A Dependency Module must only bind/provide dependencies that are both defined and implemented in its own feature")
    val ownFeatureBindingsOnly by rule {
        rationale(
            """
            If feature A binds an implementation of feature B's domain interface, feature B's DI
            graph silently depends on feature A, and removing or refactoring A breaks B's wiring
            at runtime rather than at compile time. Each feature owns its own bindings;
            cross-feature consumption goes through `:api` interfaces only.
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

    @Describe("A `:client` Dependency Module must be listed in every client shell's `ClientDependencies.kt`")
    val registeredInEveryClientShell by rule {
        rationale(
            """
            `:app:client:common` and `:app:client:web-html` each keep their own `clientDependencies`
            list, because the HTML client can't depend on the Compose UI shell. A module missing
            from one list fails only when that client first resolves a ViewModel from it.
            """.trimIndent(),
        )
        note("Reads every `app/client/*/src/**/ClientDependencies.kt`, including shells the current build doesn't include.")
        constrain { decl, _ ->
            val property = decl as? KoPropertyDeclaration ?: return@constrain emptyList()
            if (!property.isClientModule()) return@constrain emptyList()
            val reference = Regex("""\b${Regex.escape(property.name)}\b""")
            clientShellDependencyFiles
                .filterNot { file ->
                    file.text.lines().any { line -> !line.trimStart().startsWith("import ") && reference.containsMatchIn(line) }
                }
                .map { file ->
                    val shell = ":app:client:" + file.path.substringAfter("/app/client/").substringBefore("/src/")
                    Violation(decl, "`${property.name}` is missing from `clientDependencies` in `$shell`")
                }
        }
    }

    @Describe("A Dependency Module registers a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block")
    val urpcServiceBinding by rule {
        note("`bindService` (from `dev.isaacudy.udytils.urpc.koin`) registers the binding under its own concrete type, bound to `UrpcService`, with the impl resolved lazily.")
        note("Never use `scoped<UrpcService> { [Name]ServiceUrpcBinding { get() } }`: every such binding shares the `UrpcService` definition key, so co-registered services override each other and `getAll<UrpcService>()` returns only one. The test catches this form.")
        note("`urpcService(::[Name]ServiceUrpcBinding)` is the equivalent standalone form when there is no impl definition to chain off.")
        constrain { decl, _ ->
            val file = (decl as? KoContainingFileProvider)?.containingFile ?: return@constrain emptyList()
            if (Regex("""(scoped|single|factory)\s*<\s*UrpcService\s*>""").containsMatchIn(file.text)) {
                listOf(Violation(decl, "DI module binds under the shared `UrpcService` key — co-registered services override each other; chain `.bindService(::…UrpcBinding)` instead"))
            } else {
                emptyList()
            }
        }
    }
}

private val clientShellDependencyFiles by lazy {
    projectScope.files.filter { it.path.contains("/app/client/") && it.nameWithExtension == "ClientDependencies.kt" }
}
