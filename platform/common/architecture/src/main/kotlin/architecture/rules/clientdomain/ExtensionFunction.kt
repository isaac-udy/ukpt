package architecture.rules.clientdomain

import architecture.rules.shared.ExtensionFunctionRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A top-level extension function in `client.domain` that adds derived behaviour to a
    [domain model](#domain-model) or a [shared domain model](feature.md#shared-domain-model). Pure
    over its inputs — it computes from the receiver's values and touches nothing else. The mirror of
    a [shared extension function](feature.md#shared-extension-function) one level down: same shape,
    side-private receiver.

    * **Note:** The explicit receiver is what makes it an extension of the layer's vocabulary rather
      than free-standing behaviour. A top-level function with no receiver is logic, and logic here is
      a [domain interface](#domain-interface) with a [UseCase](#use-case) behind it.
    * **Note:** Convenience logic for a domain interface belongs as a default member function on the
      interface, where it stays co-located with the contract it simplifies.
    * **Note:** A helper that only one UseCase needs stays `private` inside that UseCase's file, per
      `ClientDomain.UseCase.breakDownComplexUseCases`. This construct is for an extension the layer
      shares.
""")
object ExtensionFunction : ExtensionFunctionRules<ClientDomain>()
