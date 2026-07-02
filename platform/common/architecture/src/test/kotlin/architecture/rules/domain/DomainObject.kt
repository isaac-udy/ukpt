package architecture.rules.domain

import architecture.registry.*

import architecture.definitions.isKotlinxSerializable
import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

@Describe("""
    An immutable type representing data at the domain-level.

    * **Note**: Nested types (enums, value classes, sealed interfaces/classes) belong nested
      only when conceptually inseparable from the parent — like `User.Id` or
      `Transport.Car.FuelType` in the examples below; otherwise model them as their own domain
      objects.
""")
object DomainObject : Construct<DomainLayer>(
    requirements = listOf(
        isClassOrInterface,
        oneOf(isSealed, isDataClass, isEnum, isValueClass),
        predicate("Domain objects must be annotated with `@Serializable`") { it.isKotlinxSerializable() },
    ),
) {
    @Describe("Domain objects must be immutable (val properties only)")
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

    @Describe("Should use nested value classes for identifiers where appropriate")
    val nestedValueClassIds by guidance
    @Describe("Should use sealed interface hierarchies to model polymorphic data where appropriate")
    val sealedHierarchies by guidance
    @Describe("Should include `init` blocks that enforce invariants")
    val invariantInitBlocks by guidance
    @Describe("Should use nested types when conceptually inseparable from the parent")
    val nestedTypes by guidance
}
