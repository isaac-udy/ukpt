package architecture

import architecture.definitions.DataLayer
import architecture.definitions.DomainLayer
import architecture.definitions.ServicesLayer
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.primitiveTypeNames
import architecture.definitions.validateLayer
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class DataLayerTests {
    /**
     * See [validateLayer]. Meta-test: every top-level declaration in `..data..`
     * must match exactly one [DataLayer] construct definition.
     */
    @Test
    fun validateDataLayerPackage() {
        projectScope
            .validateLayer(DataLayer)
    }

    /**
     * See [validateLayer]. Meta-test: every top-level declaration in `..services..`
     * (excluding the storage/internal/tools sub-axes) must match exactly one
     * [ServicesLayer] construct definition.
     */
    @Test
    fun validateServicesLayerPackage() {
        projectScope
            .validateLayer(ServicesLayer)
    }

    /**
     * See [validateLayer]. Meta-test: every top-level declaration in
     * `..services.internal..` must match exactly one [ServicesLayer.Internal]
     * construct definition.
     */
    @Test
    fun validateServicesLayerInternalPackage() {
        projectScope
            .validateLayer(ServicesLayer.Internal)
    }

    /**
     * See [validateLayer]. Meta-test: every top-level declaration in
     * `..services.storage..` must match exactly one [ServicesLayer.Storage]
     * construct definition.
     */
    @Test
    fun validateServicesLayerStoragePackage() {
        projectScope
            .validateLayer(ServicesLayer.Storage)
    }

    /**
     * See [validateLayer]. Meta-test: every top-level declaration in
     * `..services.tools..` must match exactly one [ServicesLayer.Tools]
     * construct definition.
     */
    @Test
    fun validateServicesLayerToolsPackage() {
        projectScope
            .validateLayer(ServicesLayer.Tools)
    }

    /**
     * See [validateLayer]. Meta-test: every top-level declaration in
     * `..data.storage..` must match exactly one [DataLayer.Storage] construct
     * definition.
     */
    @Test
    fun validateDataLayerStoragePackage() {
        projectScope
            .validateLayer(DataLayer.Storage)
    }

    // ==========================================================================
    // Section 4.3.1 Repository rules
    // ==========================================================================

    /**
     * Enforces the eager-init note under §4.3.1:
     * Repository domain interface properties must be initialized immediately,
     * not via `by lazy` or a custom getter.
     */
    @Test
    fun `repository properties must not use lazy initialization`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { DataLayer.isRepository.test(it) }
            .flatMap { it.properties() }
            .filter { it.hasPublicOrDefaultModifier }
            .assertTrue(
                additionalMessage = "[§4.3.1 (eager-init note)] Repository domain interface properties must " +
                    "be initialized immediately — no `by lazy`, no custom `get()`. Eager " +
                    "initialisation lets Koin's graph validation catch missing or cyclic " +
                    "dependencies at startup instead of the first injection at runtime, and it " +
                    "makes the wiring obvious from a quick read of the Repository constructor."
            ) { property ->
                !property.text.contains("by lazy") && !property.text.contains("get()")
            }
    }

    // ==========================================================================
    // Section 3.3 `data` package dependencies
    // ==========================================================================

    /**
     * Enforces R-DATA-02: data package must not inject domain interfaces.
     */
    @Test
    fun `data package should not inject domain interfaces`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { it.resideInPackage("..data..") }
            .flatMap { it.primaryConstructor?.parameters ?: emptyList() }
            .assertFalse(
                additionalMessage = "[R-DATA-02 §4.3.1] The data package is forbidden from injecting " +
                    "domain interfaces. Repositories *implement* domain interfaces — if a " +
                    "Repository injects one, it's calling a sibling Repository through the " +
                    "abstract layer, which makes the dependency graph unreadable and easy to " +
                    "cycle. Logic that needs multiple domain interfaces belongs in a UseCase."
            ) { param ->
                DomainLayer.isDomainInterface.test(param.type.sourceDeclaration)
                    .and(DomainLayer.inLayerPackage.test(param.type.sourceDeclaration))
            }
    }

    /**
     * Enforces §3.4.4 cross-axis dependency rules: data must not depend on UI.
     */
    @Test
    fun `data package should not depend on ui package`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { it.packagee?.name?.containsPackageSegment("data") == true }
            .assertFalse(
                additionalMessage = "[§3.4.4] The data package must not depend on ui package. UI is " +
                    "the outermost layer; `data` sits beneath it and supplies the domain " +
                    "interfaces the UI consumes. If `data` imports a UI type the layering becomes " +
                    "circular and the Repository can no longer be tested without a Compose runtime."
            ) { file ->
                file.imports.any { import ->
                    import.name.containsPackageSegment("ui")
                }
            }
    }

    // ==========================================================================
    // services.* axis dependency rules
    // ==========================================================================

    /**
     * Enforces R-SVC-01 (§3.4.4): services may depend on domain but not on data.
     * The server has no data layer; this rule prevents any services file
     * (top-level, internal, storage, tools) from importing the client-only
     * `data.storage` types.
     */
    @Test
    fun `services must not depend on data-storage`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter {
                ServicesLayer.inLayerPackage.test(it)
                    .or(ServicesLayer.Tools.inLayerPackage.test(it))
                    .or(ServicesLayer.Internal.inLayerPackage.test(it))
                    .or(ServicesLayer.Storage.inLayerPackage.test(it))
            }
            .assertTrue(
                additionalMessage = "[R-SVC-01 §3.4.4] Files in services.* must not import from " +
                    "`feature.[name].data.storage` — the server has no `data` layer. Client-side " +
                    "`data.storage` is local-device persistence (Keychain, SharedPrefs); reaching " +
                    "into it from the server would either fail at runtime or break the " +
                    "client/server split."
            ) {
                it.imports.none { import ->
                    import.name.matches(Regex("feature\\.[^.]+\\.data\\.storage\\..+"))
                }
            }
    }

    /**
     * Enforces the `CancellationException` note under §4.4.1: `@Throws` on
     * `suspend` functions in services must include CancellationException
     * (or a superclass like Exception) so Kotlin/Native can compile the function.
     */
    @Test
    fun `service suspend functions with @Throws must include CancellationException`() {
        projectScope
            .interfaces()
            .filter { it.isFeatureModule() }
            .filter { ServicesLayer.isServiceInterface.test(it) }
            .flatMap { it.functions() }
            .filter { it.hasSuspendModifier }
            .filter { it.hasAnnotation { annotation -> annotation.name == "Throws" } }
            .assertTrue(
                additionalMessage = "[§4.4.1] @Throws on suspend functions in services must include " +
                    "CancellationException (or a superclass like Exception) — required for " +
                    "Kotlin/Native compilation. Without it, kotlinc rejects the function on iOS " +
                    "targets at compile time."
            ) { function ->
                val throwsAnnotation = function.annotations.first { it.name == "Throws" }
                val text = throwsAnnotation.text
                text.contains("CancellationException::class") ||
                        Regex("""(?<!\w)Exception::class""").containsMatchIn(text)
            }
    }

    // ==========================================================================
    // services.storage isolation: must not depend on other services subpackages
    // ==========================================================================

    /**
     * Enforces R-SVC-13 (§4.4.4.1): Storage classes take/return `Row` shapes
     * (or primitives / value-class identifiers / collections) only — never
     * domain types. Domain conversion lives in mapping functions.
     */
    @Test
    fun `services-storage public methods must return Row, primitive, value class, or collection thereof`() {
        // Allowed return-type bases. The rule (per §4.4.4.1) is that Storage
        // classes take/return Row shapes only — never domain types. Allow
        // primitives, value-class identifiers, Row-suffixed types, container
        // wrappers, and Unit/Nothing.
        val rowSuffixes = listOf("Row", "Record", "Insert")
        val containerTypes = setOf("List", "Set", "Map", "Flow", "StateFlow", "SharedFlow", "Pair", "Triple")
        val timeTypes = setOf("Instant", "LocalDate", "LocalDateTime", "Duration", "UUID")

        fun isAllowedReturnTypeName(name: String): Boolean {
            // Strip nullability, generics, and whitespace so we can check the head.
            val head = name.substringBefore('<').trimEnd('?').trim()
            if (head.isEmpty()) return true
            if (head == "Unit" || head == "Nothing") return true
            if (head in primitiveTypeNames) return true
            if (head in containerTypes) return true
            if (head in timeTypes) return true
            if (rowSuffixes.any { head.endsWith(it) }) return true
            // Value-class IDs and similar: PascalCase nested names like
            // `Campaign.Path`, `Session.Path`, `AuthCredentials.UserId`.
            if (head.contains('.')) return true
            return false
        }

        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter { ServicesLayer.Storage.isStorageClass.test(it) }
            .filterNot { clazz -> ArchitectureExceptions.isExempt(clazz, "R-SVC-13") }
            .flatMap { it.functions() }
            .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
            .filterNot { it.hasOverrideModifier }
            .filter { it.returnType != null }
            .assertTrue(
                additionalMessage = "[R-SVC-13 §4.4.4.1] Storage classes in `services.storage` must " +
                    "take/return Row shapes only — never domain types. Domain conversion lives in " +
                    "mapping functions (`XxxRow.toDomain()`). If a method legitimately needs a " +
                    "domain return type, the refactor is to add a `XxxRow` data class plus a " +
                    "`toDomain()` mapping and have the ServiceImpl do the conversion."
            ) { function ->
                val typeName = function.returnType?.name ?: return@assertTrue true
                isAllowedReturnTypeName(typeName)
            }
    }

    @Test
    fun `services-storage must not depend on services-internal`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { ServicesLayer.Storage.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "[§3.4.4] Files in services.storage must not import from " +
                    "services.internal. The dependency direction inside `services` is " +
                    "`internal → storage`; Storage is the persistence layer that orchestrators " +
                    "call into, never the reverse. A storage class that reaches into internal " +
                    "would be embedding orchestration logic in the persistence layer."
            ) {
                it.imports.none { import ->
                    import.name.matches(Regex("feature\\.[^.]+\\.services\\.internal\\..+"))
                }
            }
    }

    @Test
    fun `storage classes must not inject domain interfaces, repositories, or services`() {
        projectScope
            .classes()
            .filter { it.isFeatureModule() }
            .filter {
                ServicesLayer.Storage.isStorageClass.test(it) ||
                    DataLayer.Storage.isStorageClass.test(it)
            }
            .flatMap { clazz -> clazz.primaryConstructor?.parameters.orEmpty() }
            .assertFalse(
                additionalMessage = "Storage classes are forbidden from injecting domain interfaces, " +
                    "Repositories, or Services (§3.3.1, §4.4.4.1). Storage is the lowest layer of the " +
                    "stack — it should depend on the database/keychain client and nothing higher."
            ) { param ->
                val source = param.type.sourceDeclaration as? com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
                val isDomainInterface = source != null &&
                    DomainLayer.isDomainInterface.test(source) &&
                    DomainLayer.inLayerPackage.test(source)
                val typeName = param.type.name
                isDomainInterface ||
                    typeName.endsWith("Repository") ||
                    typeName.endsWith("Service")
            }
    }

    @Test
    fun `service-defined exceptions must be Serializable`() {
        // Scope: top-level `services` package only (the cross-the-wire contract,
        // typically nested inside a Service interface). Exceptions inside
        // `services.internal.*` are server-only and don't cross the wire — they
        // don't need to be @Serializable.
        projectScope
            .classes(includeNested = true)
            .filter { it.isFeatureModule() }
            .filter { ServicesLayer.inLayerPackage.test(it) }
            .filter { clazz ->
                clazz.parents().any { parent ->
                    parent.name == "RuntimeException" ||
                        parent.name == "Exception" ||
                        parent.name == "PresentableException"
                }
            }
            .assertTrue(
                additionalMessage = "[R-PROJ-02 §5.1] Exception types defined in `services` (the " +
                    "cross-the-wire contract) must be annotated with `@Serializable`. The urpc " +
                    "transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions " +
                    "into typed payloads on the client; without @Serializable the type and message " +
                    "are lost in transit and the client receives a generic deserialisation failure " +
                    "instead. (This rule does not apply to exceptions inside `services.internal.*` " +
                    "— those stay server-side.)"
            ) { clazz ->
                clazz.hasAnnotationWithName("Serializable")
            }
    }

    @Test
    fun `services-tools must not depend on services-storage or services-internal`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { ServicesLayer.Tools.inLayerPackage.test(it) }
            .assertTrue(
                additionalMessage = "[R-SVC-02, R-SVC-25 §3.4.3, §4.4.5] Files in services.tools must " +
                    "not import from services.storage or services.internal. Tools are AI-callable " +
                    "wrappers around the Service contract — they should consume the :api Service " +
                    "interface only, not reach into Postgres tables or internal orchestrators directly."
            ) {
                it.imports.none { import ->
                    import.name.matches(Regex("feature\\.[^.]+\\.services\\.storage\\..+")) ||
                        import.name.matches(Regex("feature\\.[^.]+\\.services\\.internal\\..+"))
                }
            }
    }
}
