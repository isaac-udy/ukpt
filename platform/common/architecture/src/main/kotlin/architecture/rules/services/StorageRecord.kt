package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A hand-written persistence record shape: an `XxxRow`/`XxxRecord`/`XxxInsert` `data class`
    that lives in a feature's `services.storage`. The generated `XxxRow` classes live in
    `platform.server.postgres.tables` instead; see
    [generated `Table`/`Row` sources](#generated-tablerow-sources).
""")
object StorageRecord : Construct<ServicesLayer>(
    requirements = listOf(
        isClassWhere("is a `data class`") { it.hasDataModifier },
        oneOf(hasNameEndingWith("Row"), hasNameEndingWith("Record"), hasNameEndingWith("Insert")),
        predicate("resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    ),
)
