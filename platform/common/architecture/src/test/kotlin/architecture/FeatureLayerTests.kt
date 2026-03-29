package architecture

import architecture.definitions.FeatureLayer
import architecture.definitions.containsPackageSegment
import architecture.definitions.featureName
import architecture.definitions.isFeatureModule
import architecture.definitions.validateLayer
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class FeatureLayerTests {

    /**
     * See [validateLayer]
     */
    @Test
    fun validateFeatureLayerPackage() {
        projectScope.validateLayer(FeatureLayer)
    }

    // ==========================================================================
    // Section 4.4.1 - DI module rules
    // ==========================================================================

    @Test
    fun `service implementations must not depend on ui package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { FeatureLayer.inLayerPackage.test(it) }
            .filter { file ->
                file.declarations().any { declaration ->
                    FeatureLayer.isServiceImplementation.test(declaration)
                }
            }
            .assertFalse(
                additionalMessage = "Service implementations must not depend on ui package"
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("ui")
                }
            }
    }

    @Test
    fun `DI modules must only bind dependencies from their own feature`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { FeatureLayer.inLayerPackage.test(it) }
            .filter { file ->
                file.declarations().any { FeatureLayer.isDependencyRegistration.test(it) }
            }
            .assertTrue(
                additionalMessage = "DI modules must only bind/provide dependencies that are defined and implemented within the same feature"
            ) { file ->
                val featureName = file.featureName()
                file.imports
                    .filter { import -> import.name.startsWith("feature.") }
                    .all { import -> import.featureName() == featureName }
            }
    }

    @Test
    fun `DI bindings must not pass get() as constructor arguments`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { FeatureLayer.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "DI bindings must use constructor reference style (singleOf/scopedOf/factoryOf). " +
                        "Do not pass get() as arguments to constructors."
            ) { file ->
                file.text.lines().none { line ->
                    Regex("""[,(]\s*get\s*[<(]""").containsMatchIn(line)
                }
            }
    }
}
