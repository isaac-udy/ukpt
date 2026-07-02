package architecture.rules.services

import dev.isaacudy.udytils.architecture.*

@Describe("""
    The read/write codec for a column whose on-disk shape differs from the domain shape —
    either an `object` holding discriminator constants (e.g. `ChatMessageContentTypeCodec`,
    `ProcessingStatusCodec`) or file-private `Json` + `encode`/`decode` helpers in the
    `[Name]Mappers.kt` file.
""")
object CodecObject : Construct<ServicesLayer>(
    requirements = listOf(
        isObject,
        predicate("lives in `services.storage` alongside the Row + mapping functions for the table that uses it") { it.isInServicesSubAxis("storage") },
    ),
) {
    @Describe("A Codec encapsulates the read/write asymmetry `setFromRow` can't express — it stays small and keyed to the column it serves")
    val keyedToColumn by guidance
}
