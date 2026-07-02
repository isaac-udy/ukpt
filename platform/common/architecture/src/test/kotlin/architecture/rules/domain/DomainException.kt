package architecture.rules.domain

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A class that represents a known failure mode raised by a domain interface.

    * **Note**: Domain exceptions live at the top of the `domain` package when shared between
      multiple domain interfaces, or as a nested class on the
      [domain interface](#domain-interface) that throws them; they must be listed in `@Throws`
      on the throwing interface's primary function.
""")
object DomainException : Construct<DomainLayer>(
    requirements = listOf(
        isClassWhere("is a class extending RuntimeException/Exception/PresentableException") { decl ->
            decl.parents().any { it.name == "RuntimeException" || it.name == "Exception" || it.name == "PresentableException" }
        },
    ),
)
