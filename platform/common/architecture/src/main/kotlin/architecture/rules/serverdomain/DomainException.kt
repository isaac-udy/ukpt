package architecture.rules.serverdomain

import architecture.rules.shared.DomainExceptionRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A class named `[Name]Exception` representing a failure mode the server names and handles on
    its own — an upstream provider refusing a request, a decode that cannot be recovered.

    The counterpart of a [shared exception](feature.md#shared-exception), which is the same idea one
    level up: a failure both the client and server name, thrown by a server implementation and
    matched by client code, living in the feature root because it crosses the wire. A domain
    exception does not cross anything. Nothing outside the server can observe it, so it refactors
    freely.

    `SharedException` already draws the line: an exception that is not wire-visible belongs in
    `client.domain` or `server.domain`, not in the root. This is that home.

    * **Note:** A failure a [domain interface](#domain-interface) documents belongs in its `@Throws`,
      whichever of the two kinds it is.
""")
object DomainException : DomainExceptionRules<ServerDomain>()
