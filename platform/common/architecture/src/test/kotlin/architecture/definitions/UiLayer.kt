package architecture.definitions

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider

object UiLayer : LayerDefinition("feature..ui..") {

    val isScreen by define {
        rule("Is a NavigationDestination annotated Screen") {
            any { declaration ->
                require(declaration is KoNameProvider)
                require(declaration is KoAnnotationProvider)
                require(declaration is KoPropertyDeclaration || declaration is KoFunctionDeclaration)
                require(declaration.isTopLevel)

                val nameWithoutPlatformSuffix = declaration.name
                    .removeSuffix("Wasm")
                    .removeSuffix("Desktop")
                    .removeSuffix("Ios")
                    .removeSuffix("Android")

                val hasDestinationAnnotation = declaration.hasAnnotation { annotation ->
                    listOf(
                        "NavigationDestination",
                        "NavigationDestination.PlatformOverride"
                    ).any {
                        annotation.text.contains(it)
                    }
                }
                val hasScreenSuffix = nameWithoutPlatformSuffix.endsWith("Screen")
                hasDestinationAnnotation && hasScreenSuffix
            }
        }
        rule("Has a single ViewModel parameter (functions only)") {
            any { declaration ->
                when (declaration) {
                    is KoFunctionDeclaration -> {
                        declaration.parameters.size == 1 &&
                                declaration.parameters.single().type.name.endsWith("ViewModel")
                    }
                    is KoPropertyDeclaration -> true // Property-based screens don't have parameters
                    else -> false
                }
            }
        }
    }

    val isComposable by define {
        rule("Is not a screen") {
            any { declaration -> !isScreen.test(declaration) }
        }
        rule("Is a composable function") {
            any { declaration ->
                require(declaration is KoAnnotationProvider)
                declaration.hasAnnotation {
                    it.name == "Composable"
                }
            }
        }
    }

    val isDestination by define {
        rule("Is a class or object") { isClassOrObject() }
        rule("Has a NavigationKey parent and Destination suffix") {
            classOrObject { declaration ->
                declaration.parents().any { parent ->
                    parent.name.contains("NavigationKey")
                } && declaration.name.endsWith("Destination")
            }
        }
        rule("Is annotated with @Serializable") {
            classOrObject { declaration ->
                declaration.hasAnnotationWithName("Serializable")
            }
        }
    }

    val isViewModel by define {
        rule("Is a class") { isClass() }
        rule("Extends ViewModel and is named 'ViewModel'") {
            cls { declaration ->
                declaration.parents()
                    .any { parent -> parent.name == "ViewModel" } &&
                        declaration.name.endsWith("ViewModel")
            }
        }
        rule("Exposes only a single public 'state' property") {
            cls { declaration ->
                val publicProperties = declaration.properties()
                    .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
                publicProperties.size == 1 && publicProperties.single().name == "state"
            }
        }
        rule("State property is of ViewModelState type") {
            cls { declaration ->
                val stateProperty = declaration.properties()
                    .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
                    .singleOrNull { it.name == "state" }
                    ?: return@cls true
                stateProperty.text.contains("viewModelState") &&
                        stateProperty.text.contains(declaration.name.replace("ViewModel", "State"))
            }
        }
        rule("Has private navigation property using by navigationHandle") {
            cls { declaration ->
                val navigationProperty = declaration.properties()
                    .filter { it.hasPrivateModifier }
                    .singleOrNull { it.name == "navigation" }
                    ?: return@cls false
                val destinationName = declaration.name.replace("ViewModel", "Destination")
                Regex("""by\s+navigationHandle\s*<\s*${Regex.escape(destinationName)}\s*>""")
                    .containsMatchIn(navigationProperty.text)
            }
        }
        rule("Public functions return Unit only") {
            cls { declaration ->
                declaration.functions()
                    .filter { func -> func.hasPublicModifier || func.hasInternalModifier }
                    .filter { func -> !func.hasOverrideModifier }
                    .all { func ->
                        func.returnType?.name == "Unit" || func.returnType == null
                    }
            }
        }
    }

    val isViewModelState by define {
        rule("Is a class") { isClass() }
        rule("Is a data class named 'State'") {
            cls { declaration ->
                declaration.hasDataModifier && declaration.name.endsWith("State")
            }
        }
        rule("Is immutable (val properties only)") {
            cls { declaration ->
                declaration.properties().all { it.isVal }
            }
        }
    }

    override val layerDefinitions = listOf(
        isScreen,
        isComposable,
        isDestination,
        isViewModel,
        isViewModelState,
    )
}
