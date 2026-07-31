package architecture.rules.clientdomain

import architecture.rules.shared.ExtensionPropertyRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A top-level extension property in `client.domain` that exposes derived state on a
    [domain model](#domain-model) or a [shared domain model](feature.md#shared-domain-model).

    * **Note:** The same constraints as an [extension function](#extension-function) apply, including
      the explicit receiver: a top-level property with no receiver is state or configuration, not
      vocabulary. Prefer a property when the value is a pure projection of the receiver and is cheap
      to compute on every read.
""")
object ExtensionProperty : ExtensionPropertyRules<ClientDomain>()
