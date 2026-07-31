package architecture.rules.serverdata

import architecture.definitions.isInServerDataOutsideStorage

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A plain `internal fun` conversion between the storage `Row` shapes and domain types.

    * **Note:** The convention is `XxxRow.toDomain()` for `Row → Domain` and `Domain.toRow(...)`
      for the inverse.
""")
object MappingFunction : Construct<ServerData>(
    requirements = listOf(
        isFunction,
        predicate("resides in `feature.[name].server.data`, outside the `storage` subtree") { it.isInServerDataOutsideStorage() },
    ),
) {
    @Describe("A Mapping Function between a generated `XxxRow` and a domain type must be a plain `internal fun` declaration in `server.data`, conventionally collected in `[Name]Mappers.kt`")
    val mappersInStorage by rule { unverifiable() }
    @Describe("A storage operation that spans multiple tables to assemble a richer record must be defined as a higher-level `suspend fun [Name]Storage.loadXxx(…)` extension in `server.data`")
    val multiTableLoadHelpers by rule { unverifiable() }
}
