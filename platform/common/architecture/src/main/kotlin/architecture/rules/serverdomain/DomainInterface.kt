package architecture.rules.serverdomain

import architecture.rules.shared.DomainInterfaceRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A `fun interface` that represents a piece of domain-level business logic.

    A storage-backed interface — one a [Repository](serverdata.md#repository) provides over the
    tables its StorageClasses own — is **transaction-joining by default**: called inside a
    `platform.server.postgres.TransactionRunner.inTransaction` block, its writes are part of that
    transaction and commit or roll back with the rest of the block. That is what makes a published
    interface composable: a [UseCase](#use-case) in another feature can put one write beside its own
    and have the pair land together, without either feature naming the other's tables. An interface
    an [IntegrationClient](serverdata.md#integration-client) provides is not — it reaches outside the
    process, and belongs outside the block entirely.

    * **Note:** Default functions should use expressive names. They should provide commonly used
      functionality, such as handling a particular exception type, or simplify calling the primary
      function with particular parameters.
    * **Note:** Implementations must never override an interface's default functions. Convenience
      functions belong as default members, not top-level extensions, so they stay discoverable and
      co-located with the interface.
    * **Note:** Generic or unknown errors don't need their own exception type or `@Throws` entry.
""")
object DomainInterface : DomainInterfaceRules<ServerDomain>() {
    @Describe("A Domain Interface must be provided as a property by a Repository or an IntegrationClient, or implemented by a UseCase")
    val providedByAdapterOrUseCase by rule {
        rationale(
            """
            Domain sits between `server.services` and `server.data` and is satisfied from the far
            side: a [Repository](serverdata.md#repository) exposes the interface as a `public val`
            over the tables its StorageClasses own, an
            [IntegrationClient](serverdata.md#integration-client) does the same over something outside
            the process, or a UseCase implements it by composing several others. An interface with
            none of the three is a contract nothing answers.
            """.trimIndent(),
        )
        note("The test accepts either a class whose parents include the interface (a UseCase, or an IntegrationClient that satisfies it directly) or a `[Name]Repository`/`[Name]Client`/`[Name]Provider` with a property that references it.")
        scope(
            providedByCheck(listOf("Repository", "Client", "Provider")) { name ->
                "domain interface `$name` has no Repository or IntegrationClient property, and no UseCase implementation"
            },
        )
    }
}
