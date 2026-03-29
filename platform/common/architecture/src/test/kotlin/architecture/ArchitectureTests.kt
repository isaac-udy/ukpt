package architecture

import architecture.definitions.ConstructDefinition
import architecture.definitions.DataLayer
import architecture.definitions.DomainLayer
import architecture.definitions.FeatureLayer
import architecture.definitions.UiLayer
import architecture.definitions.featureName
import architecture.definitions.isFeatureModule
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
            DataLayer.Services,
            DataLayer.Services.Tools,
            DataLayer.Storage,
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
                additionalMessage = "",
            ) { file ->
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
                additionalMessage = "Imports must not use wildcards"
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
                additionalMessage = "Platform packages must not depend on feature packages"
            ) { file ->
                file.imports.none { import ->
                    import.name.startsWith("feature.")
                }
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
            .assertTrue { declaration ->
                return@assertTrue !declaration.text.matches(tryDeclarationRegex)
            }
    }
}
