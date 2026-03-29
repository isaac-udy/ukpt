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
     * See [validateLayer]
     */
    @Test
    fun validateUiLayerPackage() {
        projectScope.validateLayer(UiLayer)
    }

    // ==========================================================================
    // Section 3.2 `ui` package dependencies
    // ==========================================================================

    @Test
    fun `ui package should not depend on data package implementations`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".ui") == true }
            .assertFalse(
                additionalMessage = "UI package is forbidden from depending on data package (data.services kRPC interfaces are allowed)"
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("data")
                }
            }
    }

    @Test
    fun `ui package should not implement domain interfaces`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { it.resideInPackage("..ui..") }
            .assertFalse(
                additionalMessage = "UI package is forbidden from implementing domain interfaces"
            ) { clazz ->
                clazz.parents().any { parent ->
                    DomainLayer.isDomainInterface.test(parent) && DomainLayer.inLayerPackage.test(parent)
                }
            }
    }

    // ==========================================================================
    // Section 4.2.1 - Screen specific rules
    // ==========================================================================

    @Test
    fun `screens must inject ViewModels using viewModel() not koinViewModel()`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { UiLayer.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "Screens must use viewModel() to inject ViewModels, not koinViewModel()"
            ) { file ->
                file.imports.none { import ->
                    import.name.contains("koinViewModel")
                }
            }
    }

    // ==========================================================================
    // Section 4.2.3 - ViewModel specific rules
    // ==========================================================================

    @Test
    fun `ViewModels must not maintain Job references`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { UiLayer.isViewModel.test(it) }
            .assertTrue(
                additionalMessage = "ViewModels must use JobManager to manage coroutines — do not maintain var job: Job? references"
            ) { viewModel ->
                viewModel.properties()
                    .none { property ->
                        val typeName = property.type?.name.orEmpty()
                        typeName == "Job" || typeName == "Job?"
                    }
            }
    }

    // ==========================================================================
    // Section 4.2.1 - Screen injection rules
    // ==========================================================================

    @Test
    fun `ui package must not use koinInject`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { UiLayer.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "UI package must not use koinInject — all dependencies must be injected through ViewModels"
            ) { file ->
                file.imports.none { import ->
                    import.name.contains("koinInject")
                }
            }
    }
}
