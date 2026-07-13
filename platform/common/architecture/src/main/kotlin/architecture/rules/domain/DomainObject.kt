package architecture.rules.domain

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isKotlinxSerializable
import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

@Describe("""
    An immutable type that represents domain-level data.

    * **Note:** Nested types (enums, value classes, sealed interfaces/classes) belong nested only
      when conceptually inseparable from the parent, such as `User.Id` or `Transport.Car.FuelType`
      in the examples below. Otherwise, model them as their own domain objects.
""")
object DomainObject : Construct<DomainLayer>(
    requirements = listOf(
        isClassOrInterface,
        oneOf(isSealed, isDataClass, isEnum, isValueClass),
        predicate("is annotated with `@Serializable`") { it.isKotlinxSerializable() },
    ),
) {
    @Describe("A Domain Object must be immutable (val properties only)")
    val immutable by rule {
        constrain { decl, _ ->
            val props = when (decl) {
                is KoClassDeclaration -> decl.properties()
                is KoInterfaceDeclaration -> decl.properties()
                else -> return@constrain emptyList()
            }
            props.filter { it.isMutable() }.map { Violation(it, "Domain object has a mutable (`var`) property — domain objects must be immutable") }
        }
    }

    @Describe("A Domain Object should use nested value classes for identifiers where appropriate")
    val nestedValueClassIds by guidance
    @Describe("A Domain Object should use sealed interface hierarchies to model polymorphic data where appropriate")
    val sealedHierarchies by guidance
    @Describe("A Domain Object should include `init` blocks that enforce invariants")
    val invariantInitBlocks by guidance
    @Describe("A Domain Object should use nested types when conceptually inseparable from the parent")
    val nestedTypes by guidance
}
