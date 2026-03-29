package architecture

import architecture.definitions.DomainLayer
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.validateLayer
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class DomainLayerTests {

    /**
     * See [validateLayer]
     */
    @Test
    fun validateDomainLayerPackage() {
        projectScope.validateLayer(DomainLayer)
    }

    // ==========================================================================
    // Section 3.1 `domain` package dependencies
    // ==========================================================================

    @Test
    fun `domain package should not contain platform-specific dependencies`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".domain") == true }
            .assertFalse(
                additionalMessage = "Domain package must not contain platform-specific dependencies (Android, Ktor, SQL)"
            ) { file ->
                file.imports.any { import ->
                    val name = import.name
                    name.startsWith("android.") ||
                            name.startsWith("androidx.") ||
                            name.startsWith("io.ktor.") ||
                            name.contains(".sql.") ||
                            name.contains("sqldelight") ||
                            name.contains("room")
                }
            }
    }

    @Test
    fun `domain package should not depend on ui package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".domain") == true }
            .assertFalse(
                additionalMessage = "Domain package must not depend on ui package"
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("ui")
                }
            }
    }

    @Test
    fun `domain package should not depend on data package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".domain") == true }
            .assertFalse(
                additionalMessage = "Domain package must not depend on data package"
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("data")
                }
            }
    }

    // ==========================================================================
    // Section 4.1.1 - @Throws validation
    // ==========================================================================

    @Test
    fun `domain interface suspend functions with @Throws must include CancellationException`() {
        projectScope
            .interfaces()
            .filter { it.isFeatureModule() }
            .filter { DomainLayer.isDomainInterface.test(it) }
            .flatMap { it.functions() }
            .filter { it.hasSuspendModifier }
            .filter { it.hasAnnotation { annotation -> annotation.name == "Throws" } }
            .assertTrue(
                additionalMessage = "@Throws on suspend functions in domain interfaces must include CancellationException " +
                        "(required for Kotlin/Native compilation)"
            ) { function ->
                val throwsAnnotation = function.annotations.first { it.name == "Throws" }
                val text = throwsAnnotation.text
                text.contains("CancellationException::class")
            }
    }
}
