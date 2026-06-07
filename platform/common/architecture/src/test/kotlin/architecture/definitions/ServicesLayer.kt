package architecture.definitions

import architecture.definitions.ServicesLayer.isServiceImpl
import architecture.definitions.ServicesLayer.isServiceInterface
import com.lemonappdev.konsist.api.KoModifier

/**
 * The `services` axis. The contract for a feature's HTTP-style service
 * lives here on the `:api` side, and every server-side implementation
 * concern (ServiceImpl, internal helpers, Postgres tables) lives here on
 * the `:server` side.
 *
 * Internal sub-axes:
 *  * [Internal] — orchestrators and subsystem helpers (`services.internal..`)
 *  * [Storage]  — Postgres tables and Storage classes (`services.storage..`)
 *  * [Tools]    — reserved for AI tool-use subclasses (`services.tools..`); empty until ukpt adds an AI subsystem
 *
 * Top-level (`services.*` excluding the three sub-axes):
 *  * [isServiceInterface]   — :api `@Urpc` service contract
 *  * [isServiceImpl]        — :server class implementing that contract
 */
object ServicesLayer : LayerDefinition(
    rootPackage = "feature..services..",
    excludePackages = listOf(
        "feature..services..internal..",
        "feature..services..storage..",
        "feature..services..tools..",
    ),
) {
    val isServiceInterface by define {
        rule("Is interface") { isInterface() }
        // A service contract is a @Urpc interface — plain suspend/Flow functions; KSP generates
        // the client, the server binding, and the descriptors.
        rule("Is annotated @Urpc") {
            iface { declaration -> declaration.annotations.any { it.name == "Urpc" } }
        }
        rule("Is named 'Service'") { hasNameEndingWith("Service") }
    }

    val isServiceImpl by define {
        rule("Is a class") { isClass() }
        rule("Is named 'ServiceImpl'") { hasNameEndingWith("ServiceImpl") }
        rule("Is internal visibility") { hasModifier(KoModifier.INTERNAL) }
    }

    override val layerDefinitions = listOf(
        isServiceInterface,
        isServiceImpl,
    )

    /**
     * `feature.[name].services.internal..` — orchestrators (directly in the
     * bare `internal` package) and subsystems (each direct child of
     * `internal` is a sealed island under hierarchical visibility).
     */
    object Internal : LayerDefinition("feature..services..internal..") {
        val isInternalCoordinator by define {
            rule("Is a class") { isClass() }
            rule("Is not abstract") { cls { !it.hasAbstractModifier } }
            rule("Is not data class") { cls { !it.hasDataModifier } }
            rule("Is not named 'Job'") { cls { !it.name.endsWith("Job") } }
            rule("Is not named 'Exception'") { cls { !it.name.endsWith("Exception") } }
        }

        val isInternalDataCarrier by define {
            rule("Is a data class") {
                cls { it.hasDataModifier }
            }
        }

        val isInternalInterface by define {
            rule("Is an interface") { isInterface() }
        }

        val isInternalException by define {
            rule("Is a class") { isClass() }
            rule("Is named 'Exception'") { hasNameEndingWith("Exception") }
        }

        val isInternalObjectHelper by define {
            rule("Is an object") { isObject() }
        }

        override val layerDefinitions = listOf(
            isInternalCoordinator,
            isInternalDataCarrier,
            isInternalInterface,
            isInternalException,
            isInternalObjectHelper,
        )
    }

    /**
     * `feature.[name].services.storage..` — Postgres-backed Storage
     * classes plus Row↔Domain mapping helpers and codec objects.
     */
    object Storage : LayerDefinition("feature..services..storage..") {
        val isStorageClass by define {
            rule("Is a class") { isClass() }
            rule("Has a 'Storage' or 'Store' name suffix") {
                anyOf(hasNameEndingWith("Storage"), hasNameEndingWith("Store"))
            }
            rule("Is not abstract") { cls { !it.hasAbstractModifier } }
            rule("Is not data class") { cls { !it.hasDataModifier } }
            rule("Is internal visibility") { hasModifier(KoModifier.INTERNAL) }
        }

        val isStorageRecord by define {
            rule("Is a class") { isClass() }
            rule("Is a data type") { hasModifier(KoModifier.DATA) }
            rule("Has a 'Row'-like or 'Record'-like name") {
                anyOf(
                    hasNameEndingWith("Row"),
                    hasNameEndingWith("Record"),
                    hasNameEndingWith("Insert"),
                )
            }
        }

        val isMappingFunction by define {
            rule("Is a function") { isFunction() }
        }

        val isCodecObject by define {
            rule("Is an object") { isObject() }
        }

        override val layerDefinitions = listOf(
            isStorageClass,
            isStorageRecord,
            isMappingFunction,
            isCodecObject,
        )
    }

    /**
     * `feature.[name].services.tools..` — reserved for AssistantTool-style
     * AI tool-use subclasses. ukpt has no AI subsystem yet, so this layer is
     * intentionally empty: any declaration placed here will fail the
     * layer-membership meta-test until a construct is defined for it.
     */
    object Tools : LayerDefinition("feature..services..tools..") {
        override val layerDefinitions = emptyList<ConstructDefinition>()
    }
}
