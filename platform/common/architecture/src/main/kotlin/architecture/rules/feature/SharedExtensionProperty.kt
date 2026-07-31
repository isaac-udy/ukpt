package architecture.rules.feature

import architecture.definitions.isApiModule
import architecture.definitions.isFeatureRootPackage

import architecture.utils.isDomainCompatibleType
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A top-level extension property on a [shared domain model](#shared-domain-model) that exposes
    derived state.

    * **Note:** The same constraints as [shared extension functions](#shared-extension-function)
      apply, including the explicit receiver: a top-level property with no receiver is state or
      configuration, not vocabulary. Prefer a property when the value is a pure projection of the
      receiver and is cheap to compute on every read.
""")
object SharedExtensionProperty : Construct<FeatureRules>(
    requirements = listOf(
        predicate("resides in the feature root package `feature.[name]`") { it.isFeatureRootPackage() },
        predicate("is declared in the feature's `:api` module") { it.isApiModule() },
        isPropertyWhere("declares an explicit extension receiver") { it.receiverType != null },
        isPropertyWhere("has a receiver/type that is a shared domain model, primitive, or collection of those") { decl ->
            val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val typeOk = decl.type?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            receiverOk && typeOk
        },
    ),
)
