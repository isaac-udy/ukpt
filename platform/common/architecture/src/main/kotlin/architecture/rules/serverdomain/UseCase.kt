package architecture.rules.serverdomain

import architecture.rules.shared.UseCaseRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A class that implements a single [domain interface](#domain-interface).

    * **Note:** Immutable helper properties, such as loggers, are permitted. "No mutable state"
      forbids `var` properties, not properties in general.
    * **Note:** If a UseCase only injects a single other domain interface, consider whether that
      logic should become a default function of the other domain interface instead.
    * **Note:** When breaking down a complex UseCase, use file-private extension functions,
      private functions, or nested classes instead of additional domain interfaces or UseCases.
""")
object UseCase : UseCaseRules<ServerDomain>() {
    @Describe("A UseCase must not call an IntegrationClient from inside a `TransactionRunner.inTransaction` block")
    val noIntegrationCallsInsideTransactions by guidance {
        note("The block holds a pooled database connection, and any row locks it has taken, for as long as it runs — a network round trip inside it starves the pool for that whole time. Make the integration call first and open the transaction with its result in hand.")
        note("A domain interface does not say what satisfies it, so read the wiring: an interface provided by an [IntegrationClient](serverdata.md#integration-client) is the one to keep outside.")
    }
}
