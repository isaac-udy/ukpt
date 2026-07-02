package architecture.rules.domain

import architecture.registry.*

@Describe("""
    A top-level extension function on a domain object that adds derived or convenience
    behavior.

    * **Note**: Prefer default member functions on [domain interfaces](#domain-interface) for
      domain-interface convenience logic. Extension functions are appropriate for adding
      behavior to domain objects (e.g., `CampaignRole.permissions()`).
""")
object DomainExtensionFunction : Construct<DomainLayer>(
    requirements = listOf(
        isFunctionWhere("Receiver/return/parameter types are domain objects, primitives, or collections of those") { decl ->
            val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val returnOk = decl.returnType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val parametersOk = decl.parameters.all { isDomainCompatibleType(it.type.name, decl.containingFile) }
            receiverOk && returnOk && parametersOk
        },
    ),
) {
    @Describe("Domain extension functions must not introduce platform-specific dependencies")
    val noPlatformDeps by rule {
        enforcedBy("DomainLayer.noPlatformDeps")
    }
}
