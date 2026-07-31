package architecture.rules.clientdomain

import architecture.rules.shared.DomainInterfaceRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A `fun interface` that represents a piece of domain-level business logic.

    * **Note:** Default functions should use expressive names. They should provide commonly used
      functionality, such as handling a particular exception type, or simplify calling the primary
      function with particular parameters.
    * **Note:** Implementations must never override an interface's default functions. Convenience
      functions belong as default members, not top-level extensions, so they stay discoverable and
      co-located with the interface.
    * **Note:** Generic or unknown errors don't need their own exception type or `@Throws` entry.
""")
object DomainInterface : DomainInterfaceRules<ClientDomain>() {
    @Describe("A Domain Interface must be implemented by a Repository (as a property) or by a UseCase")
    val implementedByRepositoryOrUseCase by rule {
        note("The test accepts either a class whose parents include the interface (a UseCase) or a `[Name]Repository` with a property that references the interface.")
        scope(
            providedByCheck(listOf("Repository")) { name ->
                "domain interface `$name` has no Repository property or UseCase implementation"
            },
        )
    }
}
