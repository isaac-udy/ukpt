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
     * See [validateLayer]. Meta-test: every top-level declaration in the
     * feature root package must match exactly one [FeatureLayer] construct.
     */
    @Test
    fun validateFeatureLayerPackage() {
        projectScope.validateLayer(FeatureLayer)
    }

    // ==========================================================================
    // Section 4.5.1 - DI module + ServiceImpl rules
    // ==========================================================================

    /**
     * Enforces the cross-axis layering rule (§3.4.4): no axis may depend on
     * `ui`. ServiceImpls live on the server and have no Compose runtime, so
     * a UI import here either fails to compile or means a UI type has been
     * pulled out of `ui` and is being treated as data.
     */
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
                additionalMessage = "[§4.4.2 §3.4.4] Service implementations must not depend on ui " +
                    "package. ServiceImpls run on the server and have no Compose runtime — a UI " +
                    "import here would either fail to compile in :server or mean a UI type has " +
                    "been pulled out of `ui` and is being treated as data, both of which are " +
                    "wrong. If you need a shared shape with the UI, put it in the feature's " +
                    "`:api` domain or services package."
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("ui")
                }
            }
    }

    /**
     * Enforces R-FEAT-03 (§4.5.1): a feature's DI module may only bind
     * dependencies defined and implemented within the same feature.
     */
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
                additionalMessage = "[R-FEAT-03 §4.5.1] DI modules must only bind/provide dependencies " +
                    "that are defined and implemented within the same feature. If feature A binds " +
                    "an implementation of feature B's domain interface, feature B's DI graph " +
                    "silently depends on feature A — and removing/refactoring A breaks B's wiring " +
                    "at runtime, not at compile time. Each feature owns its own bindings; " +
                    "cross-feature consumption goes through `:api` interfaces only."
            ) { file ->
                val featureName = file.featureName()
                file.imports
                    .filter { import -> import.name.startsWith("feature.") }
                    .all { import -> import.featureName() == featureName }
            }
    }

    /**
     * Enforces R-FEAT-04 (§4.5.1): DI bindings must use the constructor-reference
     * style `singleOf(::Constructor).bind(...)` rather than the lambda style
     * `single<X> { Constructor(get()) }`.
     */
    @Test
    fun `DI bindings must not pass get() as constructor arguments`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { FeatureLayer.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "[R-FEAT-04 §4.5.1] DI bindings must use the constructor-reference " +
                    "style — `singleOf(::Constructor).bind(BindingType::class)`, not " +
                    "`single<BindingType> { Constructor(get()) }`. The reference style lets Koin " +
                    "validate the constructor parameters against the graph at startup; the lambda " +
                    "style hides missing or cyclic dependencies until the first injection at " +
                    "runtime."
            ) { file ->
                file.text.lines().none { line ->
                    Regex("""[,(]\s*get\s*[<(]""").containsMatchIn(line)
                }
            }
    }
}
