package architecture.rules.feature

import architecture.definitions.isApiModule
import architecture.definitions.isFeatureRootPackage

import architecture.utils.isDomainCompatibleType
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A top-level extension function on a [shared domain model](#shared-domain-model) that adds
    derived or convenience behavior, such as `User.isAdult()`. Pure over its inputs — it computes
    from the object's values and touches nothing else.

    * **Note:** The explicit receiver is what makes it an extension of the vocabulary rather than
      free-standing behaviour. A top-level function with no receiver is logic, and logic lives on a
      side.
    * **Note:** Convenience logic for a domain interface belongs as default member functions on the
      [interface](clientdomain.md#domain-interface) ([server](serverdomain.md#domain-interface))
      itself. Extension functions are for adding behavior to shared domain models.
""")
object SharedExtensionFunction : Construct<FeatureRules>(
    requirements = listOf(
        predicate("resides in the feature root package `feature.[name]`") { it.isFeatureRootPackage() },
        predicate("is declared in the feature's `:api` module") { it.isApiModule() },
        isFunctionWhere("declares an explicit extension receiver") { it.receiverType != null },
        isFunctionWhere("has receiver/return/parameter types that are shared domain models, primitives, or collections of those") { decl ->
            val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val returnOk = decl.returnType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val parametersOk = decl.parameters.all { isDomainCompatibleType(it.type.name, decl.containingFile) }
            receiverOk && returnOk && parametersOk
        },
    ),
) {
    @Describe("A shared extension function must not introduce platform-specific dependencies")
    val noPlatformDeps by rule {
        enforcedBy("FeatureRules.noPlatformDeps")
    }
}
