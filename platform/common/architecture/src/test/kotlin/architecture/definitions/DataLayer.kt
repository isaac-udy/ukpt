package architecture.definitions

import architecture.utils.collectionTypeNames
import architecture.utils.validateTypeName
import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider

object DataLayer : LayerDefinition(
    rootPackage = "feature..data..",
    excludePackages = listOf(
        "feature..data..services..",
        "feature..data..storage..",
    )
) {
    val isRepository by define {
        rule("Is a class") { isClass() }
        rule("Is named 'Repository'") { hasNameEndingWith("Repository") }
        rule("Is internal visibility") { hasModifier(KoModifier.INTERNAL) }
        rule("Is in file with matching name") {
            any {
                require(it is KoContainingFileProvider)
                require(it is KoNameProvider)
                it.name == it.containingFile.name
            }
        }
        rule("Does not implement domain interfaces") {
            cls { declaration ->
                declaration.parents().none { parent ->
                    DomainLayer.isDomainInterface.test(parent) && DomainLayer.inLayerPackage.test(
                        parent
                    )
                }
            }
        }
        rule("Does not inject domain interfaces") {
            cls { declaration ->
                declaration.primaryConstructor?.parameters.orEmpty().none { param ->
                    @Suppress("UNCHECKED_CAST")
                    val source = param.type.sourceDeclaration as? KoBaseDeclaration
                    source != null && DomainLayer.isDomainInterface.test(source) && DomainLayer.inLayerPackage.test(
                        source
                    )
                }
            }
        }
        rule("Does not inject other repositories") {
            cls { declaration ->
                declaration.primaryConstructor?.parameters.orEmpty().none { param ->
                    param.type.name.endsWith("Repository")
                }
            }
        }
        rule("Exposes domain interfaces as public val properties") {
            cls { declaration ->
                val domainImports = declaration.containingFile.imports
                    .filter { it.name.contains(".domain.") }
                    .map { it.name.substringAfterLast(".") }
                    .toSet()
                declaration.properties()
                    .filter { it.isVal }
                    .filter { it.hasPublicOrDefaultModifier }
                    .any { prop ->
                        domainImports.any { domainName -> prop.text.contains(domainName) }
                    }
            }
        }
    }

    override val layerDefinitions = listOf(
        isRepository,
    )

    object Services : LayerDefinition(
        rootPackage = "feature..data..services..",
        excludePackages = listOf("feature..data..services..tools..")
    ) {
        val isServiceInterface by define {
            rule("Is interface") { isInterface() }
            rule("Is named 'Service'") {
                hasNameEndingWith("Service")
            }
        }

        override val layerDefinitions = listOf(
            isServiceInterface,
        )

        object Tools : LayerDefinition("feature..data..services..tools..") {
            override val layerDefinitions = emptyList<ConstructDefinition>()
        }
    }

    object Storage : LayerDefinition("feature..data..storage..") {
        val isStorageClass by define {
            rule("Is a class") { isClass() }
            rule("Is named 'Storage'") { hasNameEndingWith("Storage") }
            rule("Is not abstract") { cls { !it.hasAbstractModifier } }
            rule("Is not data class") { cls { !it.hasDataModifier } }
            rule("Does not inject domain interfaces") {
                cls { declaration ->
                    declaration.primaryConstructor?.parameters.orEmpty().none { param ->
                        @Suppress("UNCHECKED_CAST")
                        val source = param.type.sourceDeclaration as? KoBaseDeclaration
                        source != null && DomainLayer.isDomainInterface.test(source) && DomainLayer.inLayerPackage.test(
                            source
                        )
                    }
                }
            }
            rule("Does not inject repositories") {
                cls { declaration ->
                    declaration.primaryConstructor?.parameters.orEmpty().none { param ->
                        param.type.name.endsWith("Repository")
                    }
                }
            }
            rule("Does not inject services") {
                cls { declaration ->
                    declaration.primaryConstructor?.parameters.orEmpty().none { param ->
                        param.type.name.endsWith("Service")
                    }
                }
            }
        }

        val isEntity by define {
            rule("Is a class") { isClass() }
            rule("Is named 'Entity'") { hasNameEndingWith("Entity") }
            rule("Is data class") { cls { it.hasDataModifier } }
            rule("Must not exist in :api module") {
                cls { declaration -> !declaration.isApiModule() }
            }
        }

        val isDocument by define {
            rule("Is a class or object") {
                classOrObject { true }
            }
            rule("Is a data type") {
                hasModifier(KoModifier.DATA)
            }
            rule("Is named 'Document'") {
                hasNameEndingWith("Document")
            }
        }

        private fun isAllowedMappingType(
            typeName: String,
            declaredIn: KoFileDeclaration,
        ): Boolean = validateTypeName(typeName, declaredIn) { typeName ->
            if (typeName.endsWith(".Companion")) return@validateTypeName true
            if (typeName in primitiveTypeNames) return@validateTypeName true
            if (typeName in collectionTypeNames) return@validateTypeName true
            if (typeName.startsWith("platform.")) return@validateTypeName true
            if (typeName.contains(".domain.")) return@validateTypeName true
            if (typeName.endsWith("Entity") || typeName.endsWith("Document")) return@validateTypeName true
            return@validateTypeName false
        }

        val isMappingFunction by define {
            rule("Is a function") { isFunction() }
            rule("Receiver type is allowed") {
                function {
                    val receiverType = it.receiverType
                        ?: return@function true
                    return@function isAllowedMappingType(
                        receiverType.name,
                        it.containingFile
                    )
                }
            }
            rule("Return type is allowed") {
                function { declaration ->
                    val returnType = declaration.returnType
                        ?: return@function true
                    return@function isAllowedMappingType(
                        returnType.name,
                        declaration.containingFile
                    )
                }
            }
            rule("Parameters are allowed") {
                function { declaration ->
                    declaration.parameters.all { param ->
                        isAllowedMappingType(param.type.name, declaration.containingFile)
                    }
                }
            }
        }

        override val layerDefinitions = listOf(
            isStorageClass,
            isEntity,
            isDocument,
            isMappingFunction,
        )
    }
}
