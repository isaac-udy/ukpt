package architecture.definitions

import architecture.ArchitectureExceptions
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.fail

fun KoScope.validateLayer(
    layer: LayerDefinition
) {
    // To validate a layer, we're going to look at all the top level declarations in that layer's
    // package and ensure that they match the criteria for that layer (e.g. one (and only one)
    // of the layer's layerDefinitions matches the declaration
    this@validateLayer
        // we only care about top-level declarations, so we can exclude nested declarations
        .declarations(includeNested = false)
        // we don't care about raw file declarations
        .filterNot { declaration -> declaration is KoFileDeclaration }
        // we're only interested in declarations that are in the layer's package, so we
        // can filter the declarations to only include those that are in the layer's package
        .filter { declaration -> layer.inLayerPackage.test(declaration) }
        // file-private declarations don't need to be validated, so we can exclude these
        .filterNot { declaration -> declaration.isPrivate() }
        // local declarations (declared inside a function body) aren't part of any
        // architectural layer — they're an implementation detail of the enclosing
        // function. Methods inside *classes* are still validated.
        .filterNot { declaration -> declaration.isInsideFunction() }
        // if a declaration is ignored through an ArchitectureException, we can exclude that
        .filterNot { declaration -> ArchitectureExceptions.isIgnored(declaration) }
        // now we're going to evaluate each of the declarations against the layerDefinitions,
        // which will give us a List<ConstructDefinition.EvaluationResult> for each declaration
        .map { declaration ->
            declaration to layer
                .layerDefinitions
                .map { definition -> definition.evaluate(declaration) }
        }
        // once we have the results, we can filter out all the declarations where the declaration
        // completely matches one single layer definition, which will leave us with all the
        // declarations that either match no ConstructDefinitions in the layer (which is bad) or
        // match multiple ConstructDefinitions in the layer (which is also bad)
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