package architecture.rules.serverdomain

import architecture.rules.shared.ConstantsRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    An `object` in `server.domain` whose only members are `val` constants: the caps, thresholds and
    named tags this side's logic agrees on — a retry budget, a batch-size ceiling. The side-private
    counterpart of [shared constants](feature.md#shared-constants) — a value both sides have to agree
    on belongs in the feature root instead, because agreement is what makes it shared.

    * **Note:** Anything with behaviour is not a constants object. A pure computation over a model
      belongs on it as an [extension function](#extension-function), and anything that composes
      contracts is a [UseCase](#use-case).
""")
object Constants : ConstantsRules<ServerDomain>()
