package architecture.registry

import architecture.definitions.containingFilePath
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider

/**
 * The `ui` layer (§3.2, §4.2) in the object style. Each construct's requirements (the
 * `🔶 construct` classification) are the predicate list in its `Construct(...)` header; its rules
 * ("what the construct must do") are `val x by rule(...)` in the body. Only the cross-cutting
 * package-dependency rules (§3.2), which aren't tied to a single construct, live at the layer level.
 * Rule ids are the exact object/property names, e.g. `UiLayer.ViewModel.usesJobManager`.
 */
object UiLayer : RuleGroup(inPackage = "feature..ui..") {

    // §4.2.1 Screens
    object Screen : Construct(
        predicate("Screen functions/properties must be bound to their Destination via the `@NavigationDestination` annotation") { declaration ->
            require(declaration is KoNameProvider)
            require(declaration is KoAnnotationProvider)
            require(declaration is KoPropertyDeclaration || declaration is KoFunctionDeclaration)
            require(declaration.isTopLevel)

            declaration.hasAnnotation { annotation ->
                listOf(
                    "NavigationDestination",
                    "NavigationDestination.PlatformOverride",
                ).any { annotation.text.contains(it) }
            }
        },
        predicate("Screen functions are named `[Name]Screen`; property-based screens end in `Screen` or `Destination`") { declaration ->
            require(declaration is KoNameProvider)
            require(declaration is KoAnnotationProvider)
            require(declaration is KoPropertyDeclaration || declaration is KoFunctionDeclaration)
            require(declaration.isTopLevel)

            val nameWithoutPlatformSuffix = declaration.name
                .removeSuffix("Wasm")
                .removeSuffix("Desktop")
                .removeSuffix("Ios")
                .removeSuffix("Android")

            // Two valid shapes:
            //  - `@Composable fun XxxScreen(...)` — function form, name must end "Screen".
            //  - `val xxxScreen|xxxDestination = navigationDestination<...>()` — property
            //    form, used when the destination needs metadata. Properties may end "Screen"
            //    or "Destination" — both are accepted because the property *is* the
            //    destination declaration site.
            when (declaration) {
                is KoFunctionDeclaration -> nameWithoutPlatformSuffix.endsWith("Screen")
                is KoPropertyDeclaration ->
                    nameWithoutPlatformSuffix.endsWith("Screen") ||
                        nameWithoutPlatformSuffix.endsWith("Destination")
                else -> false
            }
        },
        predicate("Screen functions must have a single parameter — the associated `[Name]ViewModel`") { declaration ->
            when (declaration) {
                is KoFunctionDeclaration ->
                    declaration.parameters.size == 1 &&
                        declaration.parameters.single().type.name.endsWith("ViewModel")
                is KoPropertyDeclaration -> true // Property-based screens don't have parameters
                else -> false
            }
        },
    ) {
        val composableFunction by rule("Screen functions must be annotated with `@Composable`") { guidance() }
        val viewModelStateRelationship by rule("Screen functions have a 1:1 relationship with a ViewModel and ViewModel State") { guidance() }
        val observesState by rule("Screen functions must observe the ViewModel's `state` property and use it to drive the UI") { guidance() }
        val delegatesInteraction by rule("Screen functions should delegate all user interaction handling to the ViewModel") { guidance() }
        val overlayViaDsl by rule("Dialog/overlay screens must use the `navigationDestination` DSL with `metadata = { directOverlay() }`") { guidance() }
        val overlayViewModel by rule("Dialog/overlay screens that need a ViewModel should call `viewModel()` inside the `navigationDestination` block") { guidance() }

        val screenContentCompanion by rule("Screen functions must be paired with an `internal [Name]ScreenContent` composable in the same file") {
            rationale(
                """
                The Screen function plumbs the ViewModel; the `ScreenContent` function takes only
                state + callbacks so snapshot tests can render every state without a ViewModel.
                Marking it `internal` lets the host-test source set call it; `private` makes the
                screen untestable.
                """.trimIndent(),
            )
            constrain { decl, _ ->
                val file = (decl as? KoContainingFileProvider)?.containingFile ?: return@constrain emptyList()
                val baseName = file.nameWithExtension.removeSuffix(".kt")
                if (!baseName.endsWith("Screen")) return@constrain emptyList()
                val expected = "${baseName}Content"
                val hasContent = file.functions().any { fn ->
                    fn.name == expected &&
                        fn.hasInternalModifier &&
                        fn.hasAnnotationWithName("Composable")
                }
                if (hasContent) emptyList() else listOf(Violation(decl, "Screen is missing its `internal $expected` @Composable in the same file"))
            }
        }

        val viewModelInjection by rule("ViewModels must be injected into screens using `viewModel()`, not `koinViewModel()`") {
            rationale(
                """
                `viewModel()` ties the ViewModel's lifecycle to the navigation backstack entry — when
                the entry is popped, the ViewModel is cleared. `koinViewModel()` resolves through Koin
                and either scopes to the wrong lifecycle or returns a singleton, leaking state between
                screens or returning stale state on re-entry.
                """.trimIndent(),
            )
            constrain { decl, _ ->
                val file = (decl as? KoContainingFileProvider)?.containingFile ?: return@constrain emptyList()
                if (file.imports.any { it.name.contains("koinViewModel") }) {
                    listOf(Violation(decl, "Screen file imports `koinViewModel`; inject ViewModels via `viewModel()`"))
                } else {
                    emptyList()
                }
            }
        }
    }

    // §4.2 Composables (non-Screen @Composable functions)
    object Composable : Construct(
        predicate("Is not a Screen") { declaration -> !UiLayer.Screen.test(declaration) },
        predicate("Is a `@Composable` function") { declaration ->
            require(declaration is KoAnnotationProvider)
            declaration.hasAnnotation { it.name == "Composable" }
        },
    ) {
        // §4.2.1.2 — the snapshot rule lives here because `[Name]ScreenContent` is a (non-Screen)
        // composable, not a Screen.
        val screenContentSnapshotTest by rule("Every `[Name]ScreenContent` composable must be exercised by at least one snapshot test") {
            rationale(
                """
                `ScreenContent` exists specifically so the screen body can be rendered from state +
                callbacks. Enforced softly — the test only checks that each ScreenContent is *called*
                from a `@Test` in an `androidHostTest` source set, not a minimum number of snapshots.
                """.trimIndent(),
            )
            constrain { decl, _ ->
                val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
                if (!fn.name.endsWith("ScreenContent")) return@constrain emptyList()
                if (!fn.resideInPackage("feature..ui..")) return@constrain emptyList()
                // Snapshot tests live under `src/androidHostTest/`, which `projectScope` excludes —
                // scan those files directly for a reference to each ScreenContent.
                val tested = snapshotTestSources.any { source -> source.contains("${fn.name}(") }
                if (tested) emptyList() else listOf(Violation(fn, "ScreenContent has no snapshot test that calls `${fn.name}(`"))
            }
        }
    }

    // §4.2.2 Destinations (NavigationKeys)
    object Destination : Construct(
        isClassOrObject,
        predicate("Destinations must implement `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>`") { d ->
            (d is KoClassDeclaration || d is KoObjectDeclaration) &&
                d.parents().any { parent -> parent.name.contains("NavigationKey") }
        },
        predicate("Destinations must be named `[Name]Destination`") { d ->
            (d is KoClassDeclaration || d is KoObjectDeclaration) && d.name.endsWith("Destination")
        },
        predicate("Destinations must be serializable and annotated with `@Serializable`") { d ->
            (d is KoClassDeclaration || d is KoObjectDeclaration) && d.hasAnnotationWithName("Serializable")
        },
        predicate("Destinations are declared in a file matching their name") { declaration ->
            require(declaration is KoNameProvider)
            val filePath = declaration.containingFilePath()
            val fileName = filePath.substringAfterLast("/").removeSuffix(".kt")
            fileName == declaration.name
        },
    ) {
        val minimalData by rule("Destinations should accept the minimal data required to initialise the associated Screen") { guidance() }
        val definedInApiOrClient by rule("Destinations may live in `:api` (shared entry point / server-driven) or `:client` (internal only)") { guidance() }
    }

    // §4.2.3 ViewModels
    object ViewModel : Construct(
        isClassWhere("ViewModels extend `androidx.lifecycle.ViewModel`") { declaration ->
            declaration.parents().any { parent -> parent.name == "ViewModel" }
        },
        isClassWhere("ViewModels must be named `[Name]ViewModel`") { declaration ->
            declaration.name.endsWith("ViewModel")
        },
        isClassWhere("ViewModels expose a single public `state` property, or no public properties at all") { declaration ->
            // includeNested = false: only the ViewModel's OWN properties count toward its
            // public surface — a private nested helper's public `val`s aren't part of the API.
            val publicProperties = declaration.properties(includeNested = false)
                .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
            when (publicProperties.size) {
                0 -> true // Stateless view model — pure command handler. Allowed.
                1 -> publicProperties.single().name == "state"
                else -> false
            }
        },
        isClassWhere("The `state` property is a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type)") { declaration ->
            val stateProperty = declaration.properties()
                .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
                .singleOrNull { it.name == "state" }
                ?: return@isClassWhere true
            stateProperty.text.contains("viewModelState") &&
                stateProperty.text.contains(declaration.name.replace("ViewModel", "State"))
        },
        isClassWhere("ViewModels have a `private val navigation` obtained via `navigationHandle<[Name]Destination>()`") { declaration ->
            val navigationProperty = declaration.properties()
                .filter { it.hasPrivateModifier }
                .singleOrNull { it.name == "navigation" }
                ?: return@isClassWhere false
            val destinationName = declaration.name.replace("ViewModel", "Destination")
            // Regex (not exact string match) to tolerate whitespace/line-break differences.
            Regex("""by\s+navigationHandle\s*<\s*${Regex.escape(destinationName)}\s*>""")
                .containsMatchIn(navigationProperty.text)
        },
        isClassWhere("`public`/`internal` functions on a ViewModel must only return `Unit` (or omit a return type)") { declaration ->
            declaration.functions()
                .filter { func -> func.hasPublicModifier || func.hasInternalModifier }
                .filter { func -> !func.hasOverrideModifier }
                .all { func -> func.returnType?.name == "Unit" || func.returnType == null }
        },
        predicate("ViewModels must be defined in their own file (`[Name]ViewModel.kt`)") { declaration ->
            require(declaration is KoNameProvider)
            val filePath = declaration.containingFilePath()
            val fileName = filePath.substringAfterLast("/").removeSuffix(".kt")
            fileName == declaration.name
        },
    ) {
        val injectsDomainInterfaces by rule("ViewModels should inject domain interfaces to load and manipulate domain objects") { guidance() }

        val usesJobManager by rule("ViewModels must use `JobManager` to manage coroutines — never hold `var job: Job?` references") {
            rationale(
                """
                Manual `var job: Job?` tracking is error-prone: the previous job leaks if a new one
                starts before the old one completes, and lifecycle cancellation is easy to forget.
                `dev.isaacudy.udytils.coroutines.JobManager` handles cancel-then-replace and ties
                everything to `viewModelScope`.
                """.trimIndent(),
            )
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                cls.properties()
                    .filter {
                        val typeName = it.type?.name.orEmpty()
                        typeName == "Job" || typeName == "Job?"
                    }
                    .map { Violation(it, "ViewModel holds a `Job` reference — use `JobManager` instead") }
            }
        }
    }

    // §4.2.4 ViewModel State
    object ViewModelState : Construct(
        isClass,
        isDataClass,
        hasNameEndingWith("State"),
        isClassWhere("ViewModel State objects must be immutable (val properties only)") { declaration ->
            declaration.properties().all { it.isVal }
        },
        predicate("ViewModel State objects must be defined in their own file (`[Name]State.kt`)") { declaration ->
            require(declaration is KoNameProvider)
            val filePath = declaration.containingFilePath()
            val fileName = filePath.substringAfterLast("/").removeSuffix(".kt")
            fileName == declaration.name
        },
    ) {
        val viewModelRelationship by rule("ViewModel State objects have a 1:1 relationship with a ViewModel type") { guidance() }
        val usesAsyncState by rule("ViewModel State objects must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress") { guidance() }
        val noCustomAsyncSealedTypes by rule("ViewModel State objects must not define custom sealed types for loading/success/error — use `AsyncState<T>`") { guidance() }
        val transparentContainer by rule("ViewModel State objects should be a transparent container for domain objects, not lossy UI-level mappings") { guidance() }
        val invariantInitBlocks by rule("ViewModel State objects should include `init` blocks that enforce invariants") { guidance() }
        val formattingInScreen by rule("Formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions") { guidance() }
    }

    // §4.2 UI value types (enum / sealed flow tags)
    object UiValueType : Construct(
        predicate("Is an enum, sealed class, or sealed interface") { declaration ->
            when (declaration) {
                is KoClassDeclaration -> declaration.hasEnumModifier || declaration.hasSealedModifier
                is KoInterfaceDeclaration -> declaration.hasSealedModifier
                else -> false
            }
        },
        predicate("Has no member functions") { declaration ->
            when (declaration) {
                is KoClassDeclaration -> declaration.functions().isEmpty()
                is KoInterfaceDeclaration -> declaration.functions().isEmpty()
                else -> false
            }
        },
    )

    // §3.2 ui package dependencies (layer-level — not tied to one construct)
    val mayDependOnDomain by rule("May depend on `domain`") { guidance() }

    val noImplementingDomainInterfaces by rule("Forbidden from implementing `domain` interfaces") {
        rationale(
            """
            Domain interfaces are the contract between presentation and persistence — implementations
            belong in `data` (Repositories) or `domain` (UseCases). A ViewModel that implements one
            would couple two layers' lifecycles and make the ViewModel un-injectable elsewhere.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.classes()
                .filter { it.isFeatureModule() }
                .filter { it.resideInPackage("..ui..") }
                .filterNot { exempt(it) }
                .filter { clazz ->
                    clazz.parents().any { parent ->
                        // Catalog construct (shape). The legacy rule also required the supertype to
                        // reside in feature..domain..; we approximate by shape, which is what the
                        // operator-invoke `fun interface` form effectively pins.
                        DomainLayer.DomainInterface.test(parent)
                    }
                }
                .map { Violation(it, "UI class implements a domain interface — implement it in `data` (Repository) or `domain` (UseCase) instead") }
        }
    }

    val noDataServicesDeps by rule("Forbidden from depending on `data` or `services`") {
        rationale(
            """
            UI consumes `domain` interactors only — Repositories (in `data`) fan out to `services`
            (the cross-the-wire contract) on the UI's behalf. The UI must not reach either directly.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.contains(".ui") == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import ->
                        import.name.containsPackageSegment("data") ||
                            import.name.containsPackageSegment("services")
                    }
                }
                .map { Violation(it.path, "UI file imports a `data`/`services` package") }
        }
    }

    val noKoinInject by rule("Must not use `koinInject` — all dependencies are injected through ViewModels") {
        rationale(
            """
            Resolving from Koin inside a Composable side-steps the ViewModel as the single dependency
            surface, makes the screen untestable in snapshots (no Koin runtime), and re-resolves on
            every recomposition.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.containsPackageSegment("ui") == true }
                .filterNot { exempt(it) }
                .filter { file -> file.imports.any { it.name.contains("koinInject") } }
                .map { Violation(it.path, "UI file uses `koinInject`; inject through a ViewModel instead") }
        }
    }
}

/**
 * Snapshot tests live under `src/androidHostTest/`, which `projectScope` deliberately excludes, so
 * `Composable.screenContentSnapshotTest` scans those source files directly. Computed once, lazily,
 * and reused across every `[Name]ScreenContent` checked by that rule.
 */
private val snapshotTestSources: List<String> by lazy {
    Konsist
        .scopeFromProject()
        .files
        .filter { it.path.contains("/src/androidHostTest/") }
        .map { it.text }
}
