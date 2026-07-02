package architecture.rules.ui

import architecture.registry.*
import architecture.rules.domain.DomainLayer

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

@Describe("""
    The `ui` axis spans `:api` and `:client`. **`:api` contents**: serializable Navigation Keys
    (Destinations) — a feature's shared navigation entry points. **`:client` contents**: Compose UI
    (Screens and supporting composables), ViewModels, and UI-state models. Everything the UI loads
    or mutates arrives through [domain interfaces](domain.md#domain-interface), implemented by
    [Repositories](data.md#repository) in `data` — which is also how server calls (via
    [Services](services.md#service-interface)) reach the screen.

    The layer rules below apply across the whole `feature.[name].ui` package.
""")
object UiLayer : RuleGroup(inPackage = "feature..ui..") {

    @Describe("""
        A Composable function (or property-based `navigationDestination`) that defines the layout
        and visual representation of a feature or portion of a feature.

        ### Dialog / Overlay Screens

        A Screen that is presented as a dialog or overlay on top of the current screen, rather
        than pushing onto the navigation backstack — governed by the `UiLayer.Screen.overlayViaDsl`
        and `UiLayer.Screen.overlayViewModel` rules below. Regular screens that push to the
        backstack should use the standard `@Composable fun` pattern; the property-based
        `navigationDestination` DSL is specifically for screens that need to declare custom
        metadata (such as `directOverlay()`). The property name may end in `Screen` or
        `Destination` — both are accepted because the property *is* the destination declaration
        site.
    """)
    object Screen : Construct(
        requirements = listOf(
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
        ),
    ) {
        @Describe("Screen functions must be annotated with `@Composable`")
        val composableFunction by guidance
        @Describe("Screen functions have a 1:1 relationship with a ViewModel and ViewModel State")
        val viewModelStateRelationship by guidance
        @Describe("Screen functions must observe the ViewModel's `state` property and use it to drive the UI")
        val observesState by guidance
        @Describe("Screen functions should delegate all user interaction handling to the ViewModel")
        val delegatesInteraction by guidance
        @Describe("Dialog/overlay screens must use the `navigationDestination` DSL with `metadata = { directOverlay() }`")
        val overlayViaDsl by guidance
        @Describe("Dialog/overlay screens that need a ViewModel should call `viewModel()` inside the `navigationDestination` block")
        val overlayViewModel by guidance

        @Describe("Screen functions must be paired with an `internal [Name]ScreenContent` composable in the same file")
        val screenContentCompanion by rule {
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

        @Describe("ViewModels must be injected into screens using `viewModel()`, not `koinViewModel()`")
        val viewModelInjection by rule {
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

    @Describe("""
        A `@Composable` function defined in the `..ui..` package that is **not** a Screen —
        typically a sub-component used by one or more screens, an inline editor, or a
        feature-specific overlay.

        * **Note**: `[Name]ScreenContent` companions (see `UiLayer.Screen.screenContentCompanion`)
          are non-Screen composables, which is why the snapshot-test rule lives on this construct.
          For reusable design-system primitives (buttons, fields, marks), prefer a shared
          composable in `:platform:client:ui`. Feature-local composables live alongside the Screen
          they support, and may be `internal` so snapshot tests can drive them.

        ### Snapshot tests

        A [Paparazzi](https://github.com/cashapp/paparazzi) host-side test that renders a Screen's
        `[Name]ScreenContent` and records a golden image, catching visual regressions without a
        device or emulator — enforced by `UiLayer.Composable.screenContentSnapshotTest` below.

        * **Note**: Snapshot tests live in `feature/.../src/androidHostTest/` (the host-test
          source set under AGP 9.0's KMP library plugin) and use the `SnapshotRule` helper
          (`platform.snapshot.SnapshotRule`):
            * `snapshot.screen { ... }` — screen content / composables needing bounded layout
              constraints (`fillMaxSize()` etc.); renders in a fixed-size container.
            * `snapshot.component { ... }` — small, self-sizing composables; renders at content
              size with padding.
        * **Note**: The composable under test must be `internal` (not `private`) so the host-test
          source set can reach it — the same constraint `UiLayer.Screen.screenContentCompanion`
          enforces. Add a `@Test` per meaningful state (loaded, empty, error, …) as a screen grows.
        * **Note**: Record golden images after adding or changing a snapshot test, then verify they
          match (goldens are committed under `src/androidHostTest/snapshots/images/`):

          ```
          ./gradlew :feature:core:client:recordPaparazzi
          ./gradlew :feature:core:client:verifyPaparazzi
          ```
    """)
    object Composable : Construct(
        requirements = listOf(
            predicate("Is not a Screen") { declaration -> !UiLayer.Screen.test(declaration) },
            isAnnotatedWith("Composable"),
        ),
    ) {
        @Describe("Every `[Name]ScreenContent` composable must be exercised by at least one snapshot test")
        val screenContentSnapshotTest by rule {
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

    @Describe("""
        A serializable data class or object representing the navigation contract for a particular
        screen; the input parameters required by that screen (if any) and the output result type
        provided by that screen (if any).

        * **Note**: "Minimal data" means identifiers, not payloads — a Destination should accept a
          `User.Id` and let the Screen load the associated `User`, rather than accepting an entire
          `User`.
    """)
    object Destination : Construct(
        requirements = listOf(
            isClassOrObject,
            predicate("Destinations must implement `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>`") { d ->
                (d is KoClassDeclaration || d is KoObjectDeclaration) &&
                    d.parents().any { parent -> parent.name.contains("NavigationKey") }
            },
            hasNameEndingWith("Destination"),
            isAnnotatedWith("Serializable"),
            hasFileNameMatchingDeclaration,
        ),
    ) {
        @Describe("Destinations should accept the minimal data required to initialise the associated Screen")
        val minimalData by guidance
        @Describe("Destinations may live in `:api` (shared entry point / server-driven) or `:client` (internal only)")
        val definedInApiOrClient by guidance
    }

    @Describe("""
        A class that manages the UI state for a Screen and orchestrates calls to domain interfaces
        to load data and perform side effects based on user actions.

        * **Note**: The `navigation` handle is used to read Destination parameters and perform
          navigation. When closing/completing a screen, use `NavigationHandle.close` when the user
          is cancelling or backing out, and `NavigationHandle.complete` when the user has
          successfully performed an action.
    """)
    object ViewModel : Construct(
        requirements = listOf(
            isClassWhere("ViewModels extend `androidx.lifecycle.ViewModel`") { declaration ->
                declaration.parents().any { parent -> parent.name == "ViewModel" }
            },
            isClassWhere("ViewModels must be named `[Name]ViewModel`") { declaration ->
                declaration.name.endsWith("ViewModel")
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
            hasFileNameMatchingDeclaration,
        ),
    ) {
        @Describe("ViewModels expose a single public `state` property, or no public properties at all")
        val singlePublicStateProperty by rule {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                // includeNested = false: only the ViewModel's OWN properties count toward its public surface.
                val publicProperties = cls.properties(includeNested = false)
                    .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
                val ok = when (publicProperties.size) {
                    0 -> true
                    1 -> publicProperties.single().name == "state"
                    else -> false
                }
                if (ok) emptyList() else listOf(Violation(cls, "ViewModel must expose only a single public `state` property (found: ${publicProperties.joinToString { it.name }})"))
            }
        }

        @Describe("`public`/`internal` functions on a ViewModel must only return `Unit` (or omit a return type)")
        val publicFunctionsReturnUnit by rule {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                cls.functions()
                    .filter { (it.hasPublicModifier || it.hasInternalModifier) && !it.hasOverrideModifier }
                    .filterNot { it.returnType?.name == "Unit" || it.returnType == null }
                    .map { Violation(it, "ViewModel function `${it.name}` returns `${it.returnType?.name}` — public/internal ViewModel functions must return `Unit`") }
            }
        }

        @Describe("ViewModels should inject domain interfaces to load and manipulate domain objects")
        val injectsDomainInterfaces by guidance

        @Describe("ViewModels must use `JobManager` to manage coroutines — never hold `var job: Job?` references")
        val usesJobManager by rule {
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

    @Describe("""
        The complete, immutable representation of a Screen's data at a single point in time.

        * **Note**: `AsyncState` covers action progress as well as loads — e.g. a "save" action as
          `AsyncState<Unit>`. Never directly construct `AsyncState.Loading`/`Success`/`Error` — use
          `AsyncState.fromSuspending`/`fromFlow`; that prohibition is enforced project-wide by
          `ProjectRules.noDirectAsyncStateConstruction`.
    """)
    object ViewModelState : Construct(
        requirements = listOf(
            isClass,
            isDataClass,
            hasNameEndingWith("State"),
            hasFileNameMatchingDeclaration,
        ),
    ) {
        @Describe("ViewModel State objects must be immutable (val properties only)")
        val immutable by rule {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                cls.properties().filterNot { it.isVal }.map { Violation(it, "ViewModel State property `${it.name}` is a `var` — State objects must be immutable") }
            }
        }

        @Describe("ViewModel State objects have a 1:1 relationship with a ViewModel type")
        val viewModelRelationship by guidance
        @Describe("ViewModel State objects must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress")
        val usesAsyncState by guidance
        @Describe("ViewModel State objects must not define custom sealed types for loading/success/error — use `AsyncState<T>`")
        val noCustomAsyncSealedTypes by guidance
        @Describe("ViewModel State objects should be a transparent container for domain objects, not lossy UI-level mappings")
        val transparentContainer by guidance
        @Describe("ViewModel State objects should include `init` blocks that enforce invariants")
        val invariantInitBlocks by guidance
        @Describe("Formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions")
        val formattingInScreen by guidance
    }

    @Describe("""
        A small closed value type (enum, sealed class, or sealed interface) that lives in `..ui..`
        and crosses feature boundaries — e.g. a `Slot` tag that one feature's ViewModel passes back
        to another feature's screen.

        * **Note**: If a value type grows behaviour, it stops being a value type — promote it into
          a State, Destination, or domain object as appropriate.
    """)
    object UiValueType : Construct(
        requirements = listOf(
            oneOf(isEnum, isSealed),
            predicate("Has no member functions") { declaration ->
                when (declaration) {
                    is KoClassDeclaration -> declaration.functions().isEmpty()
                    is KoInterfaceDeclaration -> declaration.functions().isEmpty()
                    else -> false
                }
            },
        ),
    )

    // §3.2 ui package dependencies (layer-level — not tied to one construct)
    @Describe("May depend on `domain`")
    val mayDependOnDomain by guidance

    @Describe("Forbidden from implementing `domain` interfaces")
    val noImplementingDomainInterfaces by rule {
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

    @Describe("Forbidden from depending on `data` or `services`")
    val noDataServicesDeps by rule {
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

    @Describe("Must not use `koinInject` — all dependencies are injected through ViewModels")
    val noKoinInject by rule {
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
