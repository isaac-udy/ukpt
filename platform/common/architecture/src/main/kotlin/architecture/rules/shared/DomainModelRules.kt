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
        rationale("Mutable state in the domain layer makes results depend on the order of earlier calls, and the layer untestable in isolation.")
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
        note("Crossing the network is the test, not carrying `@Serializable`: a payload a StorageClass writes into a column, or a state a client restores after a process death, is serialized and still private to the client or server that owns it.")
        note("Persistence is `server.data`'s concern: a model that is stored but not shared is mapped to a [storage record](serverdata.md#storage-record) there, not promoted to the root.")
        unverifiable()
    }

    @Describe("A domain model must not re-implement a concept a shared domain model already defines; use or compose the shared model instead")
    val composesSharedDomainModels by rule {
        note("The feature's vocabulary has one source of truth in the root; a private copy of a concept drifts from it as both change.")
        unverifiable()
    }
}
