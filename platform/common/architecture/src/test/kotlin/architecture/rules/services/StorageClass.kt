package architecture.rules.services

import architecture.registry.*

import architecture.definitions.primitiveTypeNames
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    The hand-written entry point to a feature's persistence — see the
    [`services.storage` overview](#servicesstorage--postgres-persistence).
""")
object StorageClass : Construct<ServicesLayer>(
    requirements = listOf(
        isClassWhere("is named `[Name]Storage` (or `[Name]Store` where the broader name fits)") { it.name.endsWith("Storage") || it.name.endsWith("Store") },
        isClassWhere("is not abstract and not a `data class`") { !it.hasAbstractModifier && !it.hasDataModifier },
        predicate("resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    ),
) {
    @Describe("A Storage class must be `internal`")
    val internalVisibility by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Storage class must be `internal`"))
        }
    }

    @Describe("A Storage class must take and return `XxxRow` types only — never domain types")
    val returnsRowTypesOnly by rule {
        rationale(
            """
            Domain conversion lives in mapping functions (`XxxRow.toDomain()`). A Storage method that
            returns a domain type embeds mapping logic in the persistence layer; the ServiceImpl should
            do the Row→Domain conversion instead.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.functions()
                .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
                .filterNot { it.hasOverrideModifier }
                .mapNotNull { fn ->
                    val typeName = fn.returnType?.name ?: return@mapNotNull null
                    if (isAllowedStorageReturnTypeName(typeName)) {
                        null
                    } else {
                        Violation(
                            fn,
                            "Storage method `${fn.name}` returns `$typeName` — services.storage may only " +
                                "take/return Row shapes (or primitives/value-class ids/collections), never domain types",
                        )
                    }
                }
        }
    }

    @Describe("A Storage operation that touches only a subset of columns keeps the hand-written `update { … it[col] = value … }` block — `setFromRow` writes every column and is wrong here")
    val partialUpdatesByHand by guidance
}

/**
 * Allowed return-type bases for a `services.storage` Storage method
 * (`ServicesLayer.StorageClass.returnsRowTypesOnly`): Row-shaped types,
 * primitives, value-class identifiers, container wrappers, time types, and `Unit`/`Nothing` — never
 * a bare domain type. Copied from the original `DataLayerTests` predicate.
 */
private fun isAllowedStorageReturnTypeName(name: String): Boolean {
    val rowSuffixes = listOf("Row", "Record", "Insert")
    val containerTypes = setOf("List", "Set", "Map", "Flow", "StateFlow", "SharedFlow", "Pair", "Triple")
    val timeTypes = setOf("Instant", "LocalDate", "LocalDateTime", "Duration", "UUID")

    // Strip nullability, generics, and whitespace so we can check the head.
    val head = name.substringBefore('<').trimEnd('?').trim()
    if (head.isEmpty()) return true
    if (head == "Unit" || head == "Nothing") return true
    if (head in primitiveTypeNames) return true
    if (head in containerTypes) return true
    if (head in timeTypes) return true
    if (rowSuffixes.any { head.endsWith(it) }) return true
    // Value-class IDs and similar: PascalCase nested names like `Campaign.Path`, `Session.Path`.
    if (head.contains('.')) return true
    return false
}
