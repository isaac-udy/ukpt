package architecture.rules.serverdata

import architecture.rules.shared.RepositoryRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    The edge of [`server.domain`](serverdomain.md): a class that provides
    [server domain interfaces](serverdomain.md#domain-interface) by exposing them as `public val`
    properties, injecting the [StorageClasses](#storage-class) it reads and writes through and
    mapping their [Rows](#storage-record) into domain objects.

    It is the [client Repository](clientdata.md#repository) on the other side, with the same name and
    the same rules. The difference is only what sits behind it: a Service and local storage on the
    client, tables on the server.

    * **Note:** The property name must match the interface name in `lowerCamelCase`, such as
      `val createUser = CreateUser { ... }`.
""")
object Repository : RepositoryRules<ServerData>(side = "server") {
    @Describe("A Repository may inject the StorageClasses it needs, and compose several of them behind one domain interface")
    val mayInjectStorage by guidance {
        note("A domain object may span several tables. Composing them is this class's job — the StorageClass under each is free to own several tables of its own, and every table has exactly one such owner.")
    }

    @Describe("A Repository must not call an IntegrationClient from inside a `TransactionRunner.inTransaction` block")
    val noIntegrationCallsInsideTransactions by guidance {
        note("The block holds a pooled database connection, and any row locks it has taken, for as long as it runs — a network round trip inside it starves the pool for that whole time. Make the integration call first and open the transaction with its result in hand.")
    }
}
