package architecture.rules.serverdata

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    An adapter onto something outside the process — a GenAI provider, a transcription service, an
    email sender, object storage. The [Repository](serverdata.md#repository) idea pointed outward
    instead of at a table, and it satisfies a [domain interface](serverdomain.md#domain-interface)
    the same two ways: by exposing it as a property, or by implementing it directly where the client
    *is* the whole of the contract.

    An integration exists to satisfy a domain interface stated in the server's own terms, not the
    vendor's. `TranscribeAudio` is the contract; that it is currently Gemini is this class's business
    and nothing else's. Swapping the provider should change one file.
""")
object IntegrationClient : Construct<ServerData>(
    requirements = listOf(
        isClass,
        isClassWhere("is named `[Name]Client` or `[Name]Provider`") { it.name.endsWith("Client") || it.name.endsWith("Provider") },
        isClassWhere("is not abstract and not a `data class`") { !it.hasAbstractModifier && !it.hasDataModifier },
    ),
) {
    @Describe("An IntegrationClient must be `internal`")
    val internalVisibility by rule {
        rationale("The vendor is an implementation detail. Callers depend on the domain interface it provides, never on the client itself.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "IntegrationClient must be `internal`"))
        }
    }

    @Describe("An IntegrationClient must not leak vendor types through the domain interface it provides")
    val noVendorTypesInContract by rule { unverifiable() }
}
