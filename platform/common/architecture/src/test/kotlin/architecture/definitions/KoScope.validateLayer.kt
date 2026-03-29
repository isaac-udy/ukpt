package architecture.definitions

import architecture.ArchitectureExceptions
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.fail

fun KoScope.validateLayer(
    layer: LayerDefinition
) {
    this@validateLayer
        .declarations(includeNested = false)
        .filterNot { declaration -> declaration is KoFileDeclaration }
        .filter { declaration -> layer.inLayerPackage.test(declaration) }
        .filterNot { declaration -> declaration.isPrivate() }
        .filterNot { declaration -> ArchitectureExceptions.isIgnored(declaration) }
        .map { declaration ->
            declaration to layer
                .layerDefinitions
                .map { definition -> definition.evaluate(declaration) }
        }
        .filterNot { (_, evaluations) ->
            evaluations.count { evaluation -> evaluation.isAllRequirementsMet } == 1
        }
        .let { nonMatchingDeclarations ->
            if (nonMatchingDeclarations.isEmpty()) return
            fail(
                buildString {
                    appendLine("Found declarations in ${layer.rootPackage} that do not match any of the ${layer.name}'s constructs:")
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
