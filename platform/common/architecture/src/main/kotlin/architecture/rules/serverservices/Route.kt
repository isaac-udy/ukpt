package architecture.rules.serverservices

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isServerModule
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

@Describe("""
    An entry point for a caller that speaks plain HTTP rather than urpc, such as a payment
    provider's webhook: a `Route.[name]` extension function in `feature.[name].server.services` of
    `:server`. The application installs it after the server host. Like a ServiceImpl, it decodes
    the request, calls domain interfaces, and answers; the work is a UseCase's.

    ```kotlin
    fun Route.paymentWebhook(configuration: PaymentWebhookConfiguration, applyPaymentEvent: ApplyPaymentEvent) {
        post("/webhooks/payments") { … }
    }
    ```
""")
object Route : Construct<ServerServices>(
    requirements = listOf(
        isFunctionWhere("is a top-level extension of Ktor's `Route`") { it.isTopLevel && it.receiverType?.name == "Route" },
        predicate("resides in `feature.[name].server.services` itself, not in a sub-package") { it.isInServicesRoot() },
        predicate("is declared in a `:server` module") { it.isServerModule() },
    ),
) {
    @Describe("A Route's parameters must be domain interfaces and configuration, never storage or another entry point")
    val takesDomainInterfaces by rule {
        rationale("The application passes what the route needs from its graph; a Repository or ServiceImpl parameter would skip the domain layer.")
        constrain { decl, _ ->
            val function = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
            function.parameters
                .filter { it.type.name.endsWith("Repository") || it.type.name.endsWith("Storage") || it.type.name.endsWith("ServiceImpl") }
                .map { Violation(function, "parameter `${it.name}` is `${it.type.name}`; take the domain interface it provides") }
        }
    }
}
