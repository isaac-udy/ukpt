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
        predicate("resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    ),
) {
    @Describe("A Mapping Function between a generated `XxxRow` and a domain type lives in `services.storage` as a plain `internal fun` declaration, conventionally collected in `[Name]Mappers.kt`")
    val mappersInStorage by guidance
    @Describe("A storage operation that spans multiple tables to assemble a richer record is defined as a higher-level `suspend fun [Name]Storage.loadXxx(…)` extension in `services.storage`")
    val multiTableLoadHelpers by guidance
}
