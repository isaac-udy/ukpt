package architecture.definitions

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.core.util.LocationUtil
import kotlin.properties.ReadOnlyProperty

abstract class LayerDefinition(
    val rootPackage: String,
    val excludePackages: List<String> = emptyList()
) {
    abstract val layerDefinitions: List<ConstructDefinition>

    val name: String get() {
        return (this::class.qualifiedName ?: this::class.simpleName)
            .orEmpty()
            .removePrefix(this::class.packageName)
            .removePrefix(".")
    }

    val inLayerPackage = DefinitionPredicate.any { declaration ->
        val inLocation =
            LocationUtil.resideInLocation(rootPackage, declaration.containingFilePackage())
        excludePackages.forEach { excludedPackage ->
            if (LocationUtil.resideInLocation(
                    excludedPackage,
                    declaration.containingFilePackage()
                )
            ) {
                return@any false
            }
        }
        return@any inLocation
    }

    val inLayer: DefinitionPredicate<KoBaseDeclaration> by lazy {
        DefinitionPredicate
            .anyOf(
                DefinitionPredicate.file { declaration ->
                    inLayerPackage.test(declaration)
                },
                *layerDefinitions
                    .map { it.asDefinitionPredicate() }
                    .toTypedArray()
            )
            .and(inLayerPackage)
    }

    fun define(
        block: ConstructDefinition.Builder.() -> Unit
    ): ReadOnlyProperty<LayerDefinition, ConstructDefinition> {
        return ReadOnlyProperty { thisRef, property ->
            val constructName = property.name.removePrefix("is")
            ConstructDefinition.define(
                layer = thisRef,
                constructName = constructName,
                block = {
                    rule("Is in package '$rootPackage'") { inLayerPackage }
                    block()
                },
            )
        }
    }
}
