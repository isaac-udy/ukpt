package architecture.rules.serverdata

import architecture.rules.shared.ConfigurationRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A `data class` named `[Name]Config` or `[Name]Configuration` in `server.data`: the settings
    of a [Repository](#repository), [StorageClass](#storage-class), or
    [IntegrationClient](#integration-client) that vary between deployments or supported modes,
    assembled by the [dependency module](feature.md#dependency-module) and injected. A setting
    fixed for every deployment is a private property of the class that uses it, not a
    configuration field.

    * **Note:** The counterpart for a [UseCase](serverdomain.md#use-case) is a
      [domain model](serverdomain.md#domain-model) in `server.domain`; the data layer's
      configuration is here because a data-layer setting names a bucket, a vendor endpoint, or a
      table, which `server.domain` never names.
    * **Note:** Reading the environment is the dependency module's job, or the `:app` shell's:
      `single { InvoicesConfig(bucket = System.getenv("INVOICES_BUCKET") ?: "invoices") }`. The
      class that receives the configuration never reads the environment itself.
""")
object Configuration : ConfigurationRules<ServerData>(side = "server")
