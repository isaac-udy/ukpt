package architecture.rules.clientdomain

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
object UseCase : UseCaseRules<ClientDomain>()
