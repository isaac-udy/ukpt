package architecture.rules.serverdata

import architecture.definitions.isInServerDataOutsideStorage

import dev.isaacudy.udytils.architecture.*

@Describe("""
    The read/write codec for a column whose on-disk shape differs from the domain shape:
    either an `object` named `[Name]Codec` holding discriminator constants (such as
    `ProcessingStatusCodec`) or file-private `Json` + `encode`/`decode` helpers in the
    `[Name]Mappers.kt` file.

    * **Note:** The name is what classifies it: "an `object` in `server.data`" would claim any
      other object the layer holds.
""")
object CodecObject : Construct<ServerData>(
    requirements = listOf(
        isObject,
        hasNameEndingWith("Codec"),
        predicate("resides in `feature.[name].server.data`, outside the `storage` subtree") { it.isInServerDataOutsideStorage() },
    ),
) {
    @Describe("A Codec should stay small and keyed to the column it serves; it encapsulates the read/write asymmetry `setFromRow` can't express")
    val keyedToColumn by guidance
}
