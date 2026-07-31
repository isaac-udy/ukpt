package architecture.rules.clientdata

import architecture.rules.shared.RepositoryRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A class that provides implementations for [domain interfaces](clientdomain.md#domain-interface) by
    exposing them as `public val` properties. The client's half of a pair: the server states the same
    construct as a [server Repository](serverdata.md#repository).

    * **Note:** The property name must match the interface name in `lowerCamelCase`, such as
      `val createUser = CreateUser { ... }`.
""")
object Repository : RepositoryRules<ClientData>(side = "client") {
    @Describe("A Repository may inject Services, `client.data.storage` Storage objects, or database clients to fulfill its domain properties")
    val mayInjectServicesStorageOrClients by guidance
}
