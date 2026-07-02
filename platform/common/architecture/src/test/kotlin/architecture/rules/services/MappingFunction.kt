package architecture.rules.services

import architecture.registry.*

@Describe("""
    Plain `internal fun` conversions between the storage `Row` shapes and domain types.

    * **Convention**: `XxxRow.toDomain()` for `Row → Domain`; `Domain.toRow(...)` for the
      inverse.
""")
object MappingFunction : Construct<ServicesLayer>(
    requirements = listOf(
        isFunction,
        predicate("Resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    ),
) {
    @Describe("Conversions between a generated `XxxRow` and a domain type live in `services.storage` as plain `internal fun` declarations, conventionally collected in `[Name]Mappers.kt`")
    val mappersInStorage by guidance
    @Describe("Where storage operations span multiple tables to assemble a richer record, define those higher-level helpers as `suspend fun [Name]Storage.loadXxx(…)` extensions in `services.storage`")
    val multiTableLoadHelpers by guidance
}
