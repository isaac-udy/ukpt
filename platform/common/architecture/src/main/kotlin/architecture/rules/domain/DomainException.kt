package architecture.rules.domain

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A class that represents a known failure mode raised by a domain interface.

    * **Note:** A domain exception lives at the top of the `domain` package when it is shared
      between multiple domain interfaces, or as a nested class on the
      [domain interface](#domain-interface) that throws it. It must be listed in `@Throws` on the
      throwing interface's primary function.
""")
object DomainException : Construct<DomainLayer>(
    requirements = listOf(
        isClassWhere("is a class extending RuntimeException/Exception/PresentableException") { decl ->
            decl.parents().any { it.name == "RuntimeException" || it.name == "Exception" || it.name == "PresentableException" }
        },
    ),
)
