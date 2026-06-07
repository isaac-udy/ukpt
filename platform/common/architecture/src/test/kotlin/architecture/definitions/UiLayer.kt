package architecture.definitions

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider

object UiLayer : LayerDefinition("feature..ui..") {

    val isScreen by define {
        rule("Is a NavigationDestination annotated Screen function or Destination property") {
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
                // Two valid shapes:
                //  - `@Composable fun XxxScreen(...)` — function form, name must end "Screen".
                //  - `val xxxScreen|xxxDestination = navigationDestination<...>()` — property
                //    form, used when the destination needs metadata (shell sections, overlay
                //    flags) declared via the property DSL. Properties may end "Screen" or
                //    "Destination" — both are accepted because the property *is* the
                //    destination declaration site.
                val hasValidName = when (declaration) {
                    is KoFunctionDeclaration -> nameWithoutPlatformSuffix.endsWith("Screen")
                    is KoPropertyDeclaration ->
                        nameWithoutPlatformSuffix.endsWith("Screen") ||
                            nameWithoutPlatformSuffix.endsWith("Destination")
                    else -> false
                }
                hasDestinationAnnotation && hasValidName
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
        rule("Has file name matching declaration name") {
            hasFileNameMatchingDeclarationName()
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
        rule("Exposes a single public 'state' property, or no public properties at all") {
            cls { declaration ->
                // includeNested = false: only the ViewModel's OWN properties count toward its
                // public surface. A private nested helper (e.g. a `private data class` holding
                // retry args) has its own public `val`s, but those aren't part of the VM's API.
                val publicProperties = declaration.properties(includeNested = false)
                    .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
                when (publicProperties.size) {
                    // Stateless view model — pure command handler. Allowed.
                    0 -> true
                    // Stateful view model — must expose `state` and nothing else.
                    1 -> publicProperties.single().name == "state"
                    else -> false
                }
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
                // Use regex instead of exact string match to tolerate whitespace/line-break
                // differences in the property declaration text
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
        rule("Has file name matching declaration name") {
            hasFileNameMatchingDeclarationName()
        }
    }

    /**
     * UI-flow value type — an enum, sealed class, or sealed interface
     * declared at the top level of a `..ui..` package. Covers cases like
     * `EventEntitySlot` where a small closed set of UI-flow tags needs to
     * cross feature boundaries (lives in `:api`, consumed by viewmodels in
     * other features).
     *
     * Pure data shapes only — no functions, no mutable state. If a type
     * grows behaviour, it stops being a value type and should move into
     * a State/Destination/ViewModel.
     */
    val isUiValueType by define {
        rule("Is an enum, sealed class, or sealed interface") {
            any { declaration ->
                when (declaration) {
                    is KoClassDeclaration ->
                        declaration.hasEnumModifier || declaration.hasSealedModifier
                    is KoInterfaceDeclaration ->
                        declaration.hasSealedModifier
                    else -> false
                }
            }
        }
        rule("Has no member functions") {
            any { declaration ->
                when (declaration) {
                    is KoClassDeclaration -> declaration.functions().isEmpty()
                    is KoInterfaceDeclaration -> declaration.functions().isEmpty()
                    else -> false
                }
            }
        }
    }

    val isViewModelState by define {
        rule("Is a class") {
            isClass()
        }
        rule("Has data modifier") {
            hasModifier(KoModifier.DATA)
        }
        rule("Is named 'State'") {
            hasNameEndingWith("State")
        }
        rule("Is immutable (val properties only)") {
            cls { declaration ->
                declaration.properties().all { it.isVal }
            }
        }
        rule("Has file name matching declaration name") {
            hasFileNameMatchingDeclarationName()
        }
    }

    override val layerDefinitions = listOf(
        isScreen,
        isComposable,
        isDestination,
        isViewModel,
        isViewModelState,
        isUiValueType,
    )
}
