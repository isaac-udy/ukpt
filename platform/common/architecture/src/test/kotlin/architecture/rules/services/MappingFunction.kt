package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    Plain `internal fun` conversions between the storage `Row` shapes and domain types.

    * **Convention**: `XxxRow.toDomain()` for `Row → Domain`; `Domain.toRow(...)` for the
      inverse.
""")
object MappingFunction : Construct<ServicesLayer>(
    requirements = listOf(
        isFunction,
        predicate("resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    ),
) {
    @Describe("A Mapping Function between a generated `XxxRow` and a domain type must be a plain `internal fun` declaration in `services.storage`, conventionally collected in `[Name]Mappers.kt`")
    val mappersInStorage by rule { unverifiable() }
    @Describe("A storage operation that spans multiple tables to assemble a richer record must be defined as a higher-level `suspend fun [Name]Storage.loadXxx(…)` extension in `services.storage`")
    val multiTableLoadHelpers by rule { unverifiable() }
}
