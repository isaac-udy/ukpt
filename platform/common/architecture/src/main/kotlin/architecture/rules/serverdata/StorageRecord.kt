package architecture.rules.serverdata

import architecture.definitions.isInServerDataStoragePackage

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A hand-written persistence record shape: an `XxxRow`/`XxxRecord`/`XxxInsert` `data class`
    that lives in a feature's `server.data.storage`, alongside the
    [StorageClass](#storage-class) that returns it. The generated `XxxRow` classes live in
    `platform.server.postgres.tables` instead; see
    [generated `Table`/`Row` sources](serverdata.md).
""")
object StorageRecord : Construct<ServerData>(
    requirements = listOf(
        isClassWhere("is a `data class`") { it.hasDataModifier },
        oneOf(hasNameEndingWith("Row"), hasNameEndingWith("Record"), hasNameEndingWith("Insert")),
        predicate("resides in `feature.[name].server.data.storage`") { it.isInServerDataStoragePackage() },
    ),
)
