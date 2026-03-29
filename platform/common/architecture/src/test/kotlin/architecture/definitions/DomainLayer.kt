package architecture.definitions

import architecture.utils.collectionTypeNames
import architecture.utils.validateTypeName
import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration

object DomainLayer : LayerDefinition("feature..domain..") {

    val isDomainInterface by define {
        rule("Is an interface") { isInterface() }
        rule("Has fun modifier") {
            iface { it.hasFunModifier}
        }
        rule("Has no sealed modifier") {
            iface { !it.hasSealedModifier }
        }
        rule("Has operator fun invoke as primary function") {
            iface { declaration ->
                declaration.functions().any { func ->
                    func.name == "invoke" && func.hasOperatorModifier
                }
            }
        }
        rule("All abstract functions are suspend or return Flow") {
            iface { declaration ->
                declaration.functions()
                    .filter { func ->
                        func.name == "invoke" || !func.text.contains("=")
                    }
                    .all { func ->
                        func.hasSuspendModifier || func.returnType?.name?.contains("Flow") == true
                    }
            }
        }
        rule("Flow-returning interfaces are prefixed with FlowOf") {
            iface { declaration ->
                val hasFlowReturn = declaration.functions().any { func ->
                    func.name == "invoke" && func.returnType?.name?.contains("Flow") == true
                }
                !hasFlowReturn || declaration.name.startsWith("FlowOf")
            }
        }
    }

    val isDomainObject by define {
        rule("Is class or interface") {
            classOrInterface { true }
        }
        rule("Is sealed/data/enum/value type") {
            hasModifier(KoModifier.SEALED)
                .or(hasModifier(KoModifier.DATA))
                .or(hasModifier(KoModifier.ENUM))
                .or(hasModifier(KoModifier.VALUE))
        }
        rule("Is @Serializable") {
            any { it.isKotlinxSerializable() }
        }
        rule("Is immutable") {
            classOrInterface { declaration ->
                declaration.properties()
                    .none { it.isMutable() }
            }
        }
    }

    val isUseCase by define {
        fun KoClassDeclaration.associatedDomainInterface(): KoParentDeclaration? {
            val parents = this.parents()
            if (parents.size != 1) return null
            val parent = parents.single()
            return parent.takeIf { isDomainInterface.test(parent) }
        }

        rule("Is a class") { isClass() }
        rule("Is not sealed, data, enum, or value type") {
            cls { declaration ->
                !declaration.hasModifier(
                    KoModifier.SEALED,
                    KoModifier.DATA,
                    KoModifier.ENUM,
                    KoModifier.VALUE,
                )
            }
        }
        rule("Implements a single domain interface") {
            cls { declaration ->
                declaration.associatedDomainInterface() != null
            }
        }
        rule("Is named '[DomainInterface]Impl'") {
            cls { declaration ->
                val domainInterfaceName = declaration.associatedDomainInterface()?.name
                declaration.name == "${domainInterfaceName}Impl"
            }
        }
        rule("All properties are immutable") {
            cls { declaration ->
                declaration.properties().all { prop ->
                    !prop.isMutable()
                }
            }
        }
    }

    val isException by define {
        rule("Is a class") { isClass() }
        rule("Extends RuntimeException or Exception") {
            cls { declaration ->
                declaration.parents()
                    .any { it.name == "RuntimeException" || it.name == "Exception" }
            }
        }
    }

    private fun isDomainCompatibleType(
        typeName: String,
        declaredIn: KoFileDeclaration,
    ): Boolean = validateTypeName(typeName, declaredIn) {
        if (it in primitiveTypeNames) return@validateTypeName true
        if (it in collectionTypeNames) return@validateTypeName true
        if (it.startsWith("platform.")) return@validateTypeName true
        if (it.contains(".domain.")) return@validateTypeName true
        return@validateTypeName false
    }

    val isDomainExtensionFunction by define {
        rule("Is a function") { isFunction() }
        rule("Receiver type is domain compatible or primitive") {
            function { declaration ->
                val receiverType = declaration.receiverType
                    ?: return@function true
                return@function isDomainCompatibleType(
                    typeName = receiverType.name,
                    declaredIn = declaration.containingFile,
                ).also {
                    if (!it) {
                        println("${declaration.name} failed receiver type check with ${receiverType.name} ${receiverType::class}")
                    }
                }
            }
        }
        rule("Return type is domain compatible or primitive") {
            function { declaration ->
                val returnType = declaration.returnType
                    ?: return@function true
                return@function isDomainCompatibleType(
                    typeName = returnType.name,
                    declaredIn = declaration.containingFile,
                )
            }
        }
        rule("Parameter types are domain compatible or primitive") {
            function { declaration ->
                declaration.parameters.all { param ->
                    isDomainCompatibleType(
                        typeName = param.type.name,
                        declaredIn = declaration.containingFile,
                    )
                }
            }
        }
    }

    override val layerDefinitions = listOf(
        isDomainInterface,
        isDomainObject,
        isUseCase,
        isException,
        isDomainExtensionFunction,
    )
}
