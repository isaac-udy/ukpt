package architecture.rules.clientdomain

import architecture.rules.shared.DomainModelRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A model that belongs to one side only: a draft being edited, a cursor, an in-flight state
    machine, a computed projection, a payload written to a column.

    The contrast with a [shared domain model](feature.md#shared-domain-model) is what the package
    split encodes, and it is **residence and reach** rather than shape. A shared domain model is part
    of the feature's shared language, named by both sides and readable by other features, so renaming
    a field is a compatibility event with cross-feature blast radius. A domain model is private to
    its side: nothing outside can observe a change, so it refactors freely.

    Serialization does not decide which of the two a type is. A domain model may carry
    `@Serializable` — a payload persisted in a column, state restored across a process death — and
    what that costs is a migration for its own stored data, never a cross-feature compatibility
    event.

    The **network** is what decides: a model the other side receives has stopped being side-private,
    and belongs in the feature root with the blast radius that comes with it.
""")
object DomainModel : DomainModelRules<ClientDomain>()
