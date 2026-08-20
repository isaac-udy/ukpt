package architecture.rules.clientdomain

import architecture.rules.shared.ConstantsRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    An `object` in `client.domain` whose only members are `val` constants: the caps, thresholds and
    named tags the client's logic agrees on. The client-private counterpart of
    [shared constants](feature.md#shared-constants) — a value both the client and server have to
    agree on belongs in the feature root instead.

    * **Note:** Anything with behaviour is not a constants object. A pure computation over a model
      belongs on it as an [extension function](#extension-function), and anything that composes
      contracts is a [UseCase](#use-case).
""")
object Constants : ConstantsRules<ClientDomain>()
