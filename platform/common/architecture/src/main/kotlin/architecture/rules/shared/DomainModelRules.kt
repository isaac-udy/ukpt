package architecture.rules.shared

import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import dev.isaacudy.udytils.architecture.*

/** The side-private domain model rules, shared by both sided domain groups. */
abstract class DomainModelRules<G : RuleGroup> : Construct<G>(
    requirements = listOf(
        isClassOrInterface,
        oneOf(isSealed, isDataClass, isEnum, isValueClass),
    ),
) {
    @Describe("A domain model must be immutable — no `var` properties")
    val immutable by rule {
        rationale("Shared mutable state in the middle of the hexagon makes call order load-bearing and defeats the layer's testability.")
        constrain { decl, _ ->
            // The construct classifies interfaces too (a sealed interface hierarchy is a model),
            // so both declaration kinds are checked.
            val props = when (decl) {
                is KoClassDeclaration -> decl.properties()
                is KoInterfaceDeclaration -> decl.properties()
                else -> emptyList()
            }
            props
                .filter { it.isMutable() }
                .map { Violation(it, "domain model has a mutable (`var`) property") }
        }
    }

    @Describe("A domain model that needs to cross the network belongs in the feature root instead")
    val notForWire by rule {
        note("Crossing the network is the test, not carrying `@Serializable`: a payload a StorageClass writes into a column, or a state a client restores after a process death, is serialized and still side-private.")
        note("Persistence is `server.data`'s concern: a model that is stored but not shared is mapped to a [storage record](serverdata.md#storage-record) there, not promoted to the root.")
        unverifiable()
    }

    @Describe("A domain model must not re-implement a concept a shared domain model already defines; use or compose the shared model instead")
    val composesSharedDomainModels by rule {
        note("The feature's language has one source of truth in the root; a side-private copy of a concept drifts from it as both change.")
        unverifiable()
    }
}
