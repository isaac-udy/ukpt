package architecture.rules.feature

import architecture.definitions.isApiModule
import architecture.definitions.isFeatureRootPackage

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isKotlinxSerializable
import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

@Describe("""
    An immutable `@Serializable` type in the feature root: a business object or concept both sides
    speak. It is the feature's vocabulary in its purest form — the highest level of abstraction it
    has for saying what it does. Because both sides name it and it is serialized across the network,
    every field is part of a compatibility surface.

    The side-private counterpart is the [domain model](clientdomain.md#domain-model)
    ([server](serverdomain.md#domain-model)), which refactors freely because nothing outside its side
    can observe the change. Same word, one qualifier: `Shared` is what says both sides name it, and
    the package is where that is written down. A side-private model may serialize too — for a column
    or for restored state — so `@Serializable` is what a shared model needs, not what distinguishes
    it.

    * **Note:** Nested types (enums, value classes, sealed interfaces/classes) belong nested only
      when conceptually inseparable from the parent, such as `User.Id` or `Transport.Car.FuelType`
      in the examples below. Otherwise, model them as their own shared domain models.
""")
object SharedDomainModel : Construct<FeatureRules>(
    requirements = listOf(
        predicate("resides in the feature root package `feature.[name]`") { it.isFeatureRootPackage() },
        predicate("is declared in the feature's `:api` module") { it.isApiModule() },
        isClassOrInterface,
        oneOf(isSealed, isDataClass, isEnum, isValueClass),
        predicate("is annotated with `@Serializable`") { it.isKotlinxSerializable() },
    ),
) {
    @Describe("A shared domain model must be immutable (val properties only)")
    val immutable by rule {
        constrain { decl, _ ->
            val props = when (decl) {
                is KoClassDeclaration -> decl.properties()
                is KoInterfaceDeclaration -> decl.properties()
                else -> return@constrain emptyList()
            }
            props.filter { it.isMutable() }.map { Violation(it, "Shared domain model has a mutable (`var`) property — shared domain models must be immutable") }
        }
    }

    @Describe("A shared domain model should use nested value classes for identifiers where appropriate")
    val nestedValueClassIds by guidance
    @Describe("A shared domain model should use sealed interface hierarchies to model polymorphic data where appropriate")
    val sealedHierarchies by guidance
    @Describe("A shared domain model should include `init` blocks that enforce invariants")
    val invariantInitBlocks by guidance
    @Describe("A shared domain model should use nested types when conceptually inseparable from the parent")
    val nestedTypes by guidance
}
