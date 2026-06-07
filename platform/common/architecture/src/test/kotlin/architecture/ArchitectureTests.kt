package architecture

import architecture.definitions.ConstructDefinition
import architecture.definitions.DataLayer
import architecture.definitions.DomainLayer
import architecture.definitions.FeatureLayer
import architecture.definitions.ServicesLayer
import architecture.definitions.UiLayer
import architecture.definitions.featureName
import architecture.definitions.isFeatureModule
import architecture.definitions.isInsideFunction
import architecture.definitions.isMutable
import architecture.definitions.isPlatformModule
import architecture.definitions.isPrivate
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test
import kotlin.test.fail

/**
 * Architecture tests based on the rules defined in README.md.
 *
 * These tests verify global/cross-cutting architectural patterns.
 * Layer-specific tests are in [DomainLayerTests], [UiLayerTests], [DataLayerTests],
 * and [FeatureLayerTests].
 */
class ArchitectureTests {

    private fun featureModuleType(path: String): String {
        val beforeSrc = path.substringBefore("/src")
        return beforeSrc.substringAfterLast("/")
    }

    /**
     * Validates that every non-private top-level declaration in a feature module belongs to
     * a known architectural layer (domain, ui, data, or feature root). Any declaration that
     * doesn't match indicates either a misplaced class or a missing layer definition.
     */
    @Test
    fun validateAllDeclarationsBelongToDefinedLayer() {
        val layers = listOf(
            DataLayer,
            DataLayer.Storage,
            ServicesLayer,
            ServicesLayer.Internal,
            ServicesLayer.Storage,
            ServicesLayer.Tools,
            DomainLayer,
            UiLayer,
            FeatureLayer,
        )
        val constructs = layers.flatMap { it.layerDefinitions }
        projectScope
            .declarations(includeNested = false)
            .filter {
                it is KoClassDeclaration || it is KoInterfaceDeclaration ||
                        it is KoObjectDeclaration || it is KoFunctionDeclaration ||
                        it is KoPropertyDeclaration
            }
            .filterNot { it.isPrivate() }
            .filterNot { it.isInsideFunction() }
            .filter { it.isFeatureModule() }
            .filterNot { ArchitectureExceptions.isIgnored(it) }
            .map { declaration ->
                declaration to constructs.map { it.evaluate(declaration) }
            }
            .filterNot { (_, evaluations) ->
                evaluations.count { evaluation -> evaluation.isAllRequirementsMet } == 1
            }
            .let { nonMatchingDeclarations ->
                if (nonMatchingDeclarations.isEmpty()) return
                fail(
                    buildString {
                        appendLine("Found declarations not matching any known architectural layer:")
                        nonMatchingDeclarations.forEach { (declaration, evaluations) ->
                            appendLine(
                                ConstructDefinition.createDebugMessage(declaration, evaluations)
                                    .prependIndent("    ")
                            )
                        }
                    }
                )
            }
    }

    @Test
    fun `named feature packages must depend on other named feature packages through api modules`() {
        val importNamesToModuleType = projectScope.declarations()
            .filterIsInstance<KoFullyQualifiedNameProvider>()
            .filter { it.fullyQualifiedName?.startsWith("feature.") == true }
            .filterIsInstance<KoContainingFileProvider>().associate {
                it as KoFullyQualifiedNameProvider
                val fullyQualifiedName = it.fullyQualifiedName!!

                it as KoContainingFileProvider
                val featureModuleType = featureModuleType(it.containingFile.path)

                fullyQualifiedName to featureModuleType
            }

        projectScope
            .files
            .filter { it.isFeatureModule() }
            .assertTrue(
                additionalMessage = "[R-MOD-04 R-MOD-06 §2.1] Cross-feature dependencies must go " +
                    "through the other feature's :api module. A feature's `:client` and `:server` " +
                    "modules are private implementation; importing from them couples features " +
                    "together and prevents either side from being refactored independently. If " +
                    "you need a type or interface from another feature, expose it via that " +
                    "feature's `:api` module.",
            ) { file ->
                val isException = ArchitectureExceptions.isFileExempt(file, "R-MOD-04", "R-MOD-06")
                if (isException) return@assertTrue true
                val featureName = file.featureName()
                file.imports
                    .filter { import ->
                        import.name.startsWith("feature.")
                    }
                    .filter { import ->
                        featureName != import.featureName()
                    }
                    .filterNot { import ->
                        importNamesToModuleType[import.name] == "api"
                    }
                    .also { imports ->
                        imports.forEach { import ->
                            println("File: ${file.path} has invalid import: ${import.name}")
                        }
                    }
                    .isEmpty()
            }
    }

    @Test
    fun `imports must not use wildcards`() {
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .assertTrue(
                additionalMessage = "[R-PROJ-07 §5.2] Imports must not use wildcards. Wildcard " +
                    "imports hide which symbols a file actually depends on, break a number of " +
                    "architecture-test checks (which inspect import names directly), and can " +
                    "quietly pull in new names when the imported package adds members. List the " +
                    "explicit imports."
            ) {
                it.imports.none { it.isWildcard }
            }
    }

    @Test
    fun `platform packages must not import from feature packages`() {
        projectScope
            .files
            .filter { it.isPlatformModule() }
            .assertTrue(
                additionalMessage = "[R-MOD-10 §2.2] Platform packages must not depend on feature " +
                    "packages. Platform is the reusable infrastructure layer; if it imported a " +
                    "feature it would no longer be feature-agnostic and couldn't be lifted into a " +
                    "shared library. If a piece of platform code needs feature-specific " +
                    "behaviour, expose an interface in `:platform` and have the feature provide " +
                    "an implementation."
            ) { file ->
                file.imports.none { import ->
                    import.name.startsWith("feature.")
                }
            }
    }

    @Test
    fun `AsyncState Loading, Success, and Error must not be constructed directly`() {
        val constructionRegex = Regex(
            pattern = """AsyncState\.(Loading|Success|Error)\s*[(<]""",
        )

        // Files that legitimately construct AsyncState values directly are
        // exempt via @file:ArchitectureException(ruleIds = ["R-UI-33"]) — see
        // §6. (They either *define* AsyncState semantics, or build AsyncState
        // values for the server-side AsyncStateDocument status pattern, which
        // doesn't fit the suspending-call shape.)
        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filterNot { ArchitectureExceptions.isFileExempt(it, "R-UI-33") }
            .assertTrue(
                additionalMessage = "[R-UI-33 §4.2.4] AsyncState.Loading, AsyncState.Success, and " +
                    "AsyncState.Error must not be constructed directly. Use " +
                    "`AsyncState.fromSuspending` or `AsyncState.fromFlow` instead — these handle " +
                    "exception capture, cancellation, and the state-flow protocol uniformly. " +
                    "Direct construction skips that machinery and silently breaks the contract " +
                    "the rest of the codebase relies on."
            ) { file ->
                file.text.lines().none { line ->
                    constructionRegex.containsMatchIn(line)
                }
            }
    }

    /**
     * Hierarchical visibility within `feature.[name].services.internal.**`:
     *
     *  * Same-package or descendant imports — always allowed.
     *  * Ancestor imports — allowed *only* when the imported declaration
     *    is a pure data shape (`data class`, `enum class`, `value class`,
     *    `data object`, `sealed class/interface`, or an `object` holding
     *    only constants). Subsystems may name shared payload types that
     *    live above them in the package tree, but may not reach upward
     *    for behaviour (regular classes, interfaces, top-level functions).
     *  * Lateral / cousin imports — always forbidden, regardless of shape.
     *
     * The intent: each direct child of `services.internal` is a sealed
     * island; cross-subsystem composition happens at the orchestrator at
     * bare `services.internal`, with shared *data* threaded through types
     * that live at a common ancestor.
     */
    @Test
    fun `services-internal hierarchical visibility`() {
        val ownFeatureInternalRegex =
            Regex("""^feature\.([^.]+)\.services\.internal(?:\.(.+))?$""")
        val internalImportRegex =
            Regex("""^feature\.([^.]+)\.services\.internal(?:\.(.+))?\.[^.]+$""")

        // Index project declarations by fully-qualified name so we can resolve
        // each violating import to the actual class/object definition and
        // inspect its modifiers.
        val declarationsByFqn: Map<String, com.lemonappdev.konsist.api.declaration.KoBaseDeclaration> =
            projectScope.declarations()
                .filterIsInstance<com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider>()
                .mapNotNull { decl ->
                    val fqn = decl.fullyQualifiedName ?: return@mapNotNull null
                    fqn to (decl as com.lemonappdev.konsist.api.declaration.KoBaseDeclaration)
                }
                .toMap()

        fun isDataShape(importName: String): Boolean {
            val target = declarationsByFqn[importName] ?: return false
            return when (target) {
                is com.lemonappdev.konsist.api.declaration.KoClassDeclaration ->
                    target.hasDataModifier ||
                        target.hasEnumModifier ||
                        target.hasValueModifier ||
                        target.hasSealedModifier
                is com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration ->
                    // Only sealed interfaces count — regular interfaces are
                    // behaviour contracts.
                    target.hasSealedModifier
                is com.lemonappdev.konsist.api.declaration.KoObjectDeclaration ->
                    // Allow `data object` and any object that's purely a
                    // constants holder (no functions, only val properties).
                    target.hasDataModifier ||
                        (target.functions().isEmpty() &&
                            target.properties().all { it.isVal && !it.isMutable() })
                else -> false
            }
        }

        projectScope
            .files
            .filter { it.isFeatureModule() }
            .filter { ServicesLayer.Internal.inLayerPackage.test(it) }
            .filterNot { file ->
                ArchitectureExceptions.isFileExempt(file, "R-SVC-09")
            }
            .assertTrue(
                additionalMessage = "[R-SVC-09 §3.4.5 §4.4.3] Files inside " +
                    "feature.[name].services.internal.<subsystem>... may import from the same " +
                    "package, a descendant, or — for pure data shapes only — an ancestor package. " +
                    "Lateral imports across subsystems are forbidden, and ancestor imports are " +
                    "forbidden for behaviour-bearing types (classes, interfaces, functions). " +
                    "Cross-subsystem composition belongs to the orchestrator at services.internal; " +
                    "shared payload types should live at a common ancestor."
            ) { file ->
                val pkg = file.packagee?.name ?: return@assertTrue true
                val ownMatch = ownFeatureInternalRegex.matchEntire(pkg) ?: return@assertTrue true
                val ownFeature = ownMatch.groupValues[1]
                val ownSubpath = ownMatch.groupValues[2] // "" for bare services.internal

                val invalidImports = file.imports.filter { import ->
                    val importMatch = internalImportRegex.matchEntire(import.name)
                        ?: return@filter false
                    val importFeature = importMatch.groupValues[1]
                    if (importFeature != ownFeature) return@filter false
                    val importSubpath = importMatch.groupValues[2] // "" for bare services.internal

                    val isSameOrDescendant = ownSubpath.isEmpty() ||
                        importSubpath == ownSubpath ||
                        importSubpath.startsWith("$ownSubpath.")
                    if (isSameOrDescendant) return@filter false

                    // Ancestor: importSubpath is empty (bare services.internal), or
                    // ownSubpath strictly extends importSubpath with a `.`.
                    val isAncestor = importSubpath.isEmpty() ||
                        ownSubpath.startsWith("$importSubpath.")
                    if (isAncestor) {
                        // Allowed iff the imported declaration is a data shape.
                        return@filter !isDataShape(import.name)
                    }
                    // Otherwise it's lateral / cousin — forbidden outright.
                    true
                }
                if (invalidImports.isNotEmpty()) {
                    println("${file.path} has invalid imports:")
                    invalidImports.forEach {
                        println("    ${it.name}")
                    }
                }
                return@assertTrue invalidImports.isEmpty()
            }
    }

    @Test
    fun `no try-catch blocks are allowed to catch 'Exception', must catch only specific exceptions or 'Throwable'`() {
        val tryDeclarationRegex = Regex(
            pattern = ".*\\btry\\s*\\{.*\\}.*\\bcatch\\s*\\(.*\\bException\\s*\\).*",
            option = RegexOption.DOT_MATCHES_ALL,
        )

        val functions = projectScope.functions()
        val properties = projectScope.properties()

        (functions + properties)
            .assertTrue(
                additionalMessage = "[R-PROJ-01 §5.1] try/catch must never catch `Exception` — " +
                    "use `catch (t: Throwable)` or catch a specific exception type instead. The " +
                    "urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side " +
                    "exceptions into types that may not extend `Exception`; a `catch (Exception)` " +
                    "block silently misses these and lets errors propagate uncaught."
            ) { declaration ->
                return@assertTrue !declaration.text.matches(tryDeclarationRegex)
            }
    }
}
