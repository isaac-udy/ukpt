package architecture

import architecture.definitions.DataLayer
import architecture.definitions.DomainLayer
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.validateLayer
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class DataLayerTests {
    /**
     * See [validateLayer]
     */
    @Test
    fun validateDataLayerPackage() {
        projectScope
            .validateLayer(DataLayer)
    }

    /**
     * See [validateLayer]
     */
    @Test
    fun validateDataLayerServicesPackage() {
        projectScope
            .validateLayer(DataLayer.Services)
    }

    /**
     * See [validateLayer]
     */
    @Test
    fun validateDataLayerServicesToolsPackage() {
        projectScope
            .validateLayer(DataLayer.Services.Tools)
    }

    /**
     * See [validateLayer]
     */
    @Test
    fun validateDataLayerStoragePackage() {
        projectScope
            .validateLayer(DataLayer.Storage)
    }

    // ==========================================================================
    // Section 4.3.1 Repository rules
    // ==========================================================================

    @Test
    fun `repository properties must not use lazy initialization`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { DataLayer.isRepository.test(it) }
            .flatMap { it.properties() }
            .filter { it.hasPublicOrDefaultModifier }
            .assertTrue(
                additionalMessage = "Repository domain interface properties must be initialized immediately — " +
                        "they must not be lazy or use custom getters"
            ) { property ->
                !property.text.contains("by lazy") && !property.text.contains("get()")
            }
    }

    // ==========================================================================
    // Section 3.3 `data` package dependencies
    // ==========================================================================

    @Test
    fun `data package should not inject domain interfaces`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { it.resideInPackage("..data..") }
            .flatMap { it.primaryConstructor?.parameters ?: emptyList() }
            .assertFalse(
                additionalMessage = "The data package is forbidden from injecting domain interfaces"
            ) { param ->
                DomainLayer.isDomainInterface.test(param.type.sourceDeclaration)
                    .and(DomainLayer.inLayerPackage.test(param.type.sourceDeclaration))
            }
    }

    @Test
    fun `data package should not depend on ui package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.containsPackageSegment("data") == true }
            .filter { it.packagee?.name?.containsPackageSegment("data.services") != true }
            .assertFalse(
                additionalMessage = "The data package must not depend on ui package"
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("ui")
                }
            }
    }

    // ==========================================================================
    // Section 3.3.1 `data.services` package rules
    // ==========================================================================

    @Test
    fun `data-services should not depend on ui package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.containsPackageSegment("data.services") == true }
            .assertFalse(
                additionalMessage = "Data services package must not depend on ui package"
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("ui")
                }
            }
    }

    // ==========================================================================
    // Section 4.3 - data.services / data.storage isolation
    // ==========================================================================

    @Test
    fun `data-services must not depend on data-storage`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter {
                DataLayer.Services.inLayerPackage.test(it)
                    .or(DataLayer.Services.Tools.inLayerPackage.test(it))
            }
            .assertTrue(
                additionalMessage = "Files in data.services must not import from data.storage"
            ) {
                it.imports.none { import ->
                    import.name.containsPackageSegment("data") &&
                        import.name.containsPackageSegment("storage")
                }
            }
    }

    @Test
    fun `service suspend functions with @Throws must include CancellationException`() {
        projectScope
            .interfaces()
            .filter { it.isFeatureModule() }
            .filter { DataLayer.Services.isServiceInterface.test(it) }
            .flatMap { it.functions() }
            .filter { it.hasSuspendModifier }
            .filter { it.hasAnnotation { annotation -> annotation.name == "Throws" } }
            .assertTrue(
                additionalMessage = "@Throws on suspend functions in services must include CancellationException " +
                        "(required for Kotlin/Native compilation)"
            ) { function ->
                val throwsAnnotation = function.annotations.first { it.name == "Throws" }
                val text = throwsAnnotation.text
                text.contains("CancellationException::class") ||
                        Regex("""(?<!\w)Exception::class""").containsMatchIn(text)
            }
    }

    // ==========================================================================
    // Section 4.3 - data.services / data.storage isolation
    // ==========================================================================

    @Test
    fun `data-storage must not depend on data-services`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { DataLayer.Storage.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "Files in data.storage must not import from data.services"
            ) {
                it.imports.none { import ->
                    import.name.containsPackageSegment("data") &&
                        import.name.containsPackageSegment("services")
                }
            }
    }
}
