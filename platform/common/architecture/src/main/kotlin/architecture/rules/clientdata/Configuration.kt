package architecture.rules.clientdata

import architecture.rules.shared.ConfigurationRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A `data class` named `[Name]Config` or `[Name]Configuration` in `client.data`: the settings
    of a [Repository](#repository) or [client data implementation](#client-data-implementation)
    that vary between deployments or supported modes, assembled by the
    [dependency module](feature.md#dependency-module) and injected. A setting fixed for every
    deployment is a private property of the class that uses it, not a configuration field.

    * **Note:** The counterpart for a [UseCase](clientdomain.md#use-case) is a
      [domain model](clientdomain.md#domain-model) in `client.domain`; the data layer's
      configuration is here because a data-layer setting names the storage or the service behind
      it, which `client.domain` never names.
""")
object Configuration : ConfigurationRules<ClientData>(side = "client")
