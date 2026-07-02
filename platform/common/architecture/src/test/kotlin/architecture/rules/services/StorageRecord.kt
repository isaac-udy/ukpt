package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    The hand-written persistence record shapes — the `XxxRow`/`XxxRecord`/`XxxInsert`
    `data class`es that live in a feature's `services.storage`. The *generated* `XxxRow`
    classes live in `platform.server.postgres.tables` instead — see
    [generated `Table`/`Row` sources](#generated-tablerow-sources).
""")
object StorageRecord : Construct<ServicesLayer>(
    requirements = listOf(
        isClassWhere("is a `data class`") { it.hasDataModifier },
        oneOf(hasNameEndingWith("Row"), hasNameEndingWith("Record"), hasNameEndingWith("Insert")),
        predicate("resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    ),
)
