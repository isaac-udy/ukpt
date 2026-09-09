package architecture.rules.shared

import architecture.definitions.containingFilePackage
import architecture.definitions.featureName
import architecture.definitions.featureNameFromContainingPackage
import architecture.definitions.isFeatureModule
import architecture.utils.isPlatformSpecificImport
import architecture.utils.publishedDomainFqns
import architecture.utils.resolvesToPublishedFqn
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import dev.isaacudy.udytils.architecture.*

/**
 * Base for the two sided domain groups (`ClientDomain`, `ServerDomain`): derives the layer's
 * `inPackage` gate from the side and carries the mechanics of the checks both sides run. The rule
 * *declarations* stay on the concrete group objects — their statements, rationales, and notes are
 * genuinely side-specific — and end in the check factories declared here, so the logic of what
 * "pure" means has one home while each side keeps its own prose.
 */
abstract class DomainGroupRules(
    private val side: String,
    constructs: List<Construct<*>>,
) : RuleGroup(
    inPackage = "feature..$side.domain..",
    constructs = constructs,
) {
    /** The layer's package path under a feature: `client.domain` / `server.domain`. */
    protected val layer: String = "$side.domain"

    private fun KoFileDeclaration.isInLayer(): Boolean =
        packagee?.name.orEmpty().let { it.contains(".$layer.") || it.endsWith(".$layer") }

    /**
     * The `pure` check: the layer imports feature roots and `:api`-published declarations of the
     * same layer only. Own-feature imports of the layer are the layer itself, not an import out
     * of it; other features' layer imports must resolve to a declaration residing in `:api`.
     */
    protected fun pureCheck(): ScopeCheck = ScopeCheck { scope, exempt ->
        val published = publishedDomainFqns(scope, layer)
        scope.files
            .filter { it.isFeatureModule() && it.isInLayer() }
            .filterNot { exempt(it) }
            .flatMap { file ->
                val ownFeature = file.featureNameFromContainingPackage()
                file.imports
                    .filter { it.name.startsWith("feature.") }
                    .filter { it.name.contains(".client.") || it.name.contains(".server.") }
                    .filterNot { it.name.contains(".$layer.") && it.featureName() == ownFeature }
                    .filterNot { it.name.contains(".$layer.") && resolvesToPublishedFqn(it.name, published) }
                    .map { Violation(file.path, "$layer imports non-root feature code `${it.name}`") }
            }
    }

    /** The platform-dependency check; [violation] carries the side's own framing of what leaked in. */
    protected fun noPlatformDepsCheck(violation: String): ScopeCheck = ScopeCheck { scope, exempt ->
        scope.files
            .filter { it.isFeatureModule() && it.isInLayer() }
            .filterNot { exempt(it) }
            .filter { file -> file.imports.any { it.name.isPlatformSpecificImport() } }
            .map { Violation(it.path, violation) }
    }

    /**
     * The inventory audit: one finding per feature that declares a domain interface on this side,
     * counting interfaces, domain models (in the layer and in the feature root), UseCases, and
     * how many interfaces have no production consumer or exactly one.
     */
    protected fun inventoryAudit(): ScopeCheck = ScopeCheck { scope, _ ->
        val graph = scope.domainInterfaceGraph(side)
        val modelsByFeature = scope.declarations(includeNested = false)
            .filter { it.isFeatureModule() && it.isModelShape() }
            .filter { decl ->
                val pkg = decl.containingFilePackage()
                pkg.contains(".$layer") || !pkg.contains(".client.") && !pkg.contains(".server.") &&
                    !pkg.endsWith(".client") && !pkg.endsWith(".server")
            }
            .groupingBy { it.featureName() }
            .eachCount()
        graph.byFeature().toSortedMap().map { (feature, nodes) ->
            val published = nodes.count { it.published }
            val useCases = nodes.count { it.hasUseCase }
            val unconsumed = nodes.count { it.consumers.isEmpty() }
            val single = nodes.count { it.consumers.size == 1 }
            val several = nodes.size - unconsumed - single
            Violation(
                "feature.$feature $layer",
                "${nodes.size} domain interfaces ($published published), " +
                    "${modelsByFeature[feature] ?: 0} domain models, $useCases UseCases; " +
                    "consumers per interface: none $unconsumed, one $single, several $several",
            )
        }
    }

    private fun KoBaseDeclaration.isModelShape(): Boolean = when (this) {
        is KoClassDeclaration -> hasDataModifier || hasSealedModifier || hasEnumModifier || hasValueModifier
        is KoInterfaceDeclaration -> hasSealedModifier
        else -> false
    }
}
