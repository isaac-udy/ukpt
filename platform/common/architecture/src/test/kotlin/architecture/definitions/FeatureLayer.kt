package architecture.definitions

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration

object FeatureLayer : LayerDefinition(
    rootPackage = "feature..",
    excludePackages = listOf(
        "feature..data..",
        "feature..domain..",
        "feature..services..",
        "feature..ui..",
    )
) {

    val isDependencyRegistration by define {
        rule("Is a property") { isProperty() }
        rule("Is named 'Dependencies'") { hasNameEndingWith("Dependencies") }
    }

    val isDependencyRegistrationHelper by define {
        rule("Is a function") { isFunction() }
        rule("Is internal visibility") { hasModifier(KoModifier.INTERNAL) }
        rule("Has Module receiver") {
            function { declaration ->
                declaration.receiverType?.name == "Module"
            }
        }
    }

    val isServiceImplementation by define {
        rule("Is a class") { isClass() }
        rule("Is named 'ServiceImpl'") { hasNameEndingWith("ServiceImpl") }
        rule("Is in server module") { cls { it.isServerModule() } }
        rule("Is internal visibility") { hasModifier(KoModifier.INTERNAL) }
        rule("Implements a service interface") {
            cls { declaration ->
                declaration.parents().any { ServicesLayer.isServiceInterface.test(it) }
            }
        }
        rule("Does not inject domain interfaces") {
            cls { declaration ->
                declaration.primaryConstructor?.parameters.orEmpty().none { param ->
                    val source = param.type.sourceDeclaration as? KoBaseDeclaration
                    source != null && DomainLayer.isDomainInterface.test(source) && DomainLayer.inLayerPackage.test(source)
                }
            }
        }
    }

    override val layerDefinitions = listOf(
        isDependencyRegistration,
        isDependencyRegistrationHelper,
        isServiceImplementation,
    )
}
