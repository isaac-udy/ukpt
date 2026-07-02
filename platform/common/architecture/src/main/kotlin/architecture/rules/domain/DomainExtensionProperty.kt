package architecture.rules.domain

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A top-level extension property on a domain object that exposes derived state.

    * **Note**: Same constraints as [domain extension functions](#domain-extension-function).
      Prefer a property when the value is a pure projection of the receiver and is cheap to
      compute on every read.
""")
object DomainExtensionProperty : Construct<DomainLayer>(
    requirements = listOf(
        isPropertyWhere("has a receiver/type that is a domain object, primitive, or collection of those") { decl ->
            val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val typeOk = decl.type?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            receiverOk && typeOk
        },
    ),
)
