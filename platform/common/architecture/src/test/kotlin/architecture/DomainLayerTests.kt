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
     * See [validateLayer]. Meta-test: every top-level declaration in `..domain..`
     * must match exactly one [DomainLayer] construct definition.
     */
    @Test
    fun validateDomainLayerPackage() {
        projectScope.validateLayer(DomainLayer)
    }

    // ==========================================================================
    // Section 3.1 `domain` package dependencies
    // ==========================================================================

    /**
     * Enforces R-DOM-01: domain packages must not contain platform-specific dependencies.
     */
    @Test
    fun `domain package should not contain platform-specific dependencies`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".domain") == true }
            .assertFalse(
                additionalMessage = "[R-DOM-01] Domain package must not contain platform-specific " +
                    "dependencies (Android, Ktor, SQL, sqldelight, room). The domain layer is " +
                    "meant to be pure Kotlin so it stays portable across :client and :server, runs " +
                    "on every Kotlin Multiplatform target, and stays unit-testable without " +
                    "instantiating a platform runtime. If you need a platform call, expose it " +
                    "through a domain interface and implement it in `data` (client) or `services` " +
                    "(server)."
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

    /**
     * Enforces R-DOM-02 (domain must not depend on `ui` or `data`); §3.4.4 cross-axis rules.
     */
    @Test
    fun `domain package should not depend on ui package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".domain") == true }
            .assertFalse(
                additionalMessage = "[R-DOM-02] Domain package must not depend on ui package " +
                    "(§3.4.4). The dependency graph is `ui → domain ← data`; pulling UI types into " +
                    "the domain would invert that and let presentation concerns leak into the layer " +
                    "that's meant to stay pure. If a domain object needs a UI representation, that " +
                    "mapping lives on the Screen / State, not in the domain."
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("ui")
                }
            }
    }

    /**
     * Enforces R-DOM-02 (domain must not depend on `ui` or `data`).
     */
    @Test
    fun `domain package should not depend on data package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".domain") == true }
            .assertFalse(
                additionalMessage = "[R-DOM-02] Domain package must not depend on data package " +
                    "(§3.4.4). Repositories in `data` *implement* the domain interfaces; a reverse " +
                    "import would create a cycle. If you need a piece of state in the domain, " +
                    "expose it through a domain interface and let the Repository fulfil it."
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("data")
                }
            }
    }

    @Test
    fun `domain package should not depend on services package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.contains(".domain") == true }
            .assertFalse(
                additionalMessage = "Domain package must not depend on services package. " +
                    "Domain is the deepest layer (§3.4.4); services depends on domain, not the other " +
                    "way around. Calling the server is the responsibility of Repositories in `data`, " +
                    "which expose Domain Interfaces for the UI/domain to consume."
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("services")
                }
            }
    }

    // ==========================================================================
    // Section 4.1.1 - @Throws validation
    // ==========================================================================

    /**
     * Enforces R-DOM-19 (§4.1.3): a UseCase must not override the default
     * functions provided by its domain interface — the only member it may
     * implement is the primary `operator fun invoke`.
     */
    @Test
    fun `UseCases must not override default functions of their domain interface`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { DomainLayer.isUseCase.test(it) }
            .flatMap { it.functions() }
            .filter { it.hasOverrideModifier }
            .assertTrue(
                additionalMessage = "[R-DOM-19 §4.1.3] UseCases must not override default functions of " +
                    "their domain interface. The only abstract member on a domain interface is the " +
                    "primary `operator fun invoke` — every other function is a default. Overriding " +
                    "a default in a UseCase makes the convenience helpers behave differently per " +
                    "implementation, which defeats the point of placing them on the interface."
            ) { function ->
                function.name == "invoke"
            }
    }

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
                additionalMessage = "[R-DOM-10 §4.1.1] @Throws on suspend functions in domain " +
                    "interfaces must include CancellationException (or a superclass like " +
                    "Exception) — required for Kotlin/Native compilation. Without it, kotlinc " +
                    "rejects the function on iOS targets at compile time."
            ) { function ->
                val throwsAnnotation = function.annotations.first { it.name == "Throws" }
                val text = throwsAnnotation.text
                text.contains("CancellationException::class") ||
                        Regex("""(?<!\w)Exception::class""").containsMatchIn(text)
            }
    }
}
