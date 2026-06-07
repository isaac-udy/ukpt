package architecture

import architecture.definitions.DomainLayer
import architecture.definitions.UiLayer
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.validateLayer
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class UiLayerTests {

    /**
     * See [validateLayer]. Meta-test: every top-level declaration in `..ui..`
     * must match exactly one [UiLayer] construct definition.
     */
    @Test
    fun validateUiLayerPackage() {
        projectScope.validateLayer(UiLayer)
    }

    // ==========================================================================
    // Section 3.2 `ui` package dependencies
    // ==========================================================================

    /**
     * Enforces R-UI-03: UI must not depend on `data` or `services`.
     */
    @Test
    fun `ui package should not depend on data or services packages`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".ui") == true }
            .assertFalse(
                additionalMessage = "[R-UI-03] UI package is forbidden from depending on data " +
                    "(Repositories + client local storage) or services (the cross-the-wire " +
                    "contract). UI consumes domain interactors only — Repositories fan out to " +
                    "services on the UI's behalf."
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("data") ||
                        import.name.containsPackageSegment("services")
                }
            }
    }

    /**
     * Enforces R-UI-02: UI is forbidden from implementing domain interfaces.
     */
    @Test
    fun `ui package should not implement domain interfaces`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { it.resideInPackage("..ui..") }
            .assertFalse(
                additionalMessage = "[R-UI-02] UI package is forbidden from implementing domain " +
                    "interfaces (§3.2). Domain interfaces are the contract between presentation " +
                    "and persistence — implementations belong in `data` (Repositories) or in " +
                    "`domain` (UseCases). A ViewModel that implements one would couple two layers' " +
                    "lifecycles and make the ViewModel un-injectable into another presentation " +
                    "context."
            ) { clazz ->
                clazz.parents().any { parent ->
                    DomainLayer.isDomainInterface.test(parent) && DomainLayer.inLayerPackage.test(parent)
                }
            }
    }

    // ==========================================================================
    // Section 4.2.1 / 4.2.3 - Screen + ViewModel injection rules
    // ==========================================================================

    /**
     * Enforces R-UI-27: ViewModels must be injected via `viewModel()`, not `koinViewModel()`.
     */
    @Test
    fun `screens must inject ViewModels using viewModel() not koinViewModel()`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { UiLayer.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "[R-UI-27] Screens must use `viewModel()` to inject ViewModels, " +
                    "not `koinViewModel()` (§4.2.3). `viewModel()` ties the ViewModel's lifecycle " +
                    "to the navigation backstack entry — when the entry is popped, the ViewModel " +
                    "is cleared. `koinViewModel()` resolves through Koin's container and either " +
                    "scopes to the wrong lifecycle or returns a singleton, causing leaked state " +
                    "between screens or stale state on re-entry."
            ) { file ->
                file.imports.none { import ->
                    import.name.contains("koinViewModel")
                }
            }
    }

    /**
     * Enforces R-UI-28: ViewModels must use [JobManager] to manage coroutines.
     */
    @Test
    fun `ViewModels must not maintain Job references`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { UiLayer.isViewModel.test(it) }
            .assertTrue(
                additionalMessage = "[R-UI-28] ViewModels must use `JobManager` to manage coroutines " +
                    "— do not maintain `var job: Job?` references (§4.2.3). Manual `var job: Job?` " +
                    "tracking is error-prone: the previous job leaks if a new one starts before " +
                    "the old one completes, and lifecycle cancellation is easy to forget. " +
                    "JobManager handles cancel-then-replace and ties everything to viewModelScope."
            ) { viewModel ->
                viewModel.properties()
                    .none { property ->
                        val typeName = property.type?.name.orEmpty()
                        typeName == "Job" || typeName == "Job?"
                    }
            }
    }

    /**
     * Enforces R-UI-11 (§4.2.1): every Screen is paired with an `internal`
     * `[Name]ScreenContent` @Composable in the same file, so snapshot tests
     * can render the screen body from state + callbacks without a ViewModel.
     */
    @Test
    fun `every Screen must have an internal ScreenContent companion in the same file`() {
        // For each top-level @NavigationDestination Screen (function or property)
        // declared in a file named [Name]Screen.kt, the same file must declare an
        // internal @Composable function named [Name]ScreenContent.
        //
        // The pair lets snapshot tests render the screen body without going through
        // a ViewModel — see §4.2.1 of the architecture README.
        val screenFiles = projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { UiLayer.inLayerPackage.test(it) }
            .filter { file ->
                val fileName = file.nameWithExtension.removeSuffix(".kt")
                if (!fileName.endsWith("Screen")) return@filter false
                file.declarations(includeNested = false).any { decl ->
                    UiLayer.isScreen.test(decl)
                }
            }

        screenFiles.assertTrue(
            additionalMessage = "[R-UI-11 §4.2.1] Every Screen must be paired with an internal " +
                "`[Name]ScreenContent` @Composable in the same file. The Screen function plumbs the ViewModel; " +
                "the `ScreenContent` function takes only state + callbacks so snapshot tests can " +
                "render every state of the screen without instantiating a ViewModel. Marking it " +
                "`internal` lets the test source set call it; `private` makes the screen untestable."
        ) { file ->
            val baseName = file.nameWithExtension.removeSuffix(".kt")
            val expected = "${baseName}Content"
            file.functions().any { fn ->
                fn.name == expected &&
                    fn.hasInternalModifier &&
                    fn.hasAnnotationWithName("Composable")
            }
        }
    }

    @Test
    fun `ui package must not use koinInject`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { UiLayer.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "[§4.2.3] UI package must not use `koinInject` — all dependencies " +
                    "must be injected through ViewModels. Resolving from Koin inside a Composable " +
                    "side-steps the ViewModel as the single dependency surface, makes the screen " +
                    "untestable in snapshots (no Koin runtime), and re-resolves on every " +
                    "recomposition."
            ) { file ->
                file.imports.none { import ->
                    import.name.contains("koinInject")
                }
            }
    }
}
