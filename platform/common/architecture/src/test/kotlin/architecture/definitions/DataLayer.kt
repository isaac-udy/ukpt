package architecture.definitions

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider

/**
 * The `data` axis is **client-only**. It holds:
 *  * Repositories — `feature.[name].data.<Name>Repository`
 *  * Local persistence (Keychain, SharedPrefs, …) — `feature.[name].data.storage`
 *
 * Server-side persistence and service implementations live in the
 * [ServicesLayer]'s sub-axes (`services.storage`, `services.internal`, …),
 * not under `data` anymore.
 */
object DataLayer : LayerDefinition(
    rootPackage = "feature..data..",
    excludePackages = listOf(
        "feature..data..storage..",
    ),
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

    /**
     * Client-side abstractions that aren't repositories — e.g.
     * `BinaryUploadClient`, an interface with platform-specific
     * implementations for chunked file upload. These don't fit the
     * Repository shape but legitimately live in `feature.[name].data`
     * because they're consumed by client-side Repositories.
     */
    val isClientDataInterface by define {
        rule("Is an interface") { isInterface() }
    }

    val isClientDataImplementation by define {
        rule("Is a class") { isClass() }
        rule("Is not a Repository") { cls { !it.name.endsWith("Repository") } }
    }

    override val layerDefinitions = listOf(
        isRepository,
        isClientDataInterface,
        isClientDataImplementation,
    )

    /**
     * Client-side local persistence (e.g. AuthCredentialStorage). Server-
     * side storage moved to [ServicesLayer.Storage].
     */
    object Storage : LayerDefinition("feature..data..storage..") {
        val isStorageClass by define {
            rule("Is a class") { isClass() }
            rule("Is named 'Storage'") { hasNameEndingWith("Storage") }
            rule("Is not abstract") { cls { !it.hasAbstractModifier } }
            rule("Is not data class") { cls { !it.hasDataModifier } }
        }

        override val layerDefinitions = listOf(isStorageClass)
    }
}
