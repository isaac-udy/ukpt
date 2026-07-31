package architecture.rules.serverdata

import architecture.definitions.isInServerDataStoragePackage

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.primitiveTypeNames
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    The single write path for the tables it owns, and the only place their queries are written. A
    StorageClass speaks [Rows](#storage-record): it takes and returns persistence shapes, and names
    no domain type at all. It lives in `feature.[name].server.data.storage`, the layer's Row-only
    subpackage, which mirrors [`client.data.storage`](clientdata.md#client-storage). The
    [Repository](#repository) above it, at the `server.data` root, injects it, maps what it returns,
    and provides the [domain interfaces](serverdomain.md#domain-interface) callers actually hold. See
    the [`server.data` overview](serverdata.md).

    **Ownership runs from the table.** A StorageClass may own several tables, and should when they
    change together — a reservation that locks and increments two counters in one transaction has one
    set of invariants, and two classes would hold half of it each. What a table may not have is two
    owners: `ServerData.tableOwnedBySingleStorage` gives every table exactly one class that writes
    it, so its rules and side effects live in one place and cannot be skipped by going around it.
""")
object StorageClass : Construct<ServerData>(
    requirements = listOf(
        isClassWhere("is named `[Name]Storage` (or `[Name]Store` where the broader name fits)") { it.name.endsWith("Storage") || it.name.endsWith("Store") },
        isClassWhere("is not abstract and not a `data class`") { !it.hasAbstractModifier && !it.hasDataModifier },
        predicate("resides in `feature.[name].server.data.storage`") { it.isInServerDataStoragePackage() },
    ),
) {
    @Describe("A Storage class must be `internal`")
    val internalVisibility by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Storage class must be `internal`"))
        }
    }

    @Describe("A Storage class must take and return `XxxRow` types only, never domain types")
    val returnsRowTypesOnly by rule {
        rationale(
            """
            The query and the mapping are two things, and this class is the query. Domain conversion
            is the [Repository](serverdata.md#repository)'s job, through this layer's
            [mapping functions](serverdata.md#mapping-function) (`XxxRow.toDomain()`). A Storage
            method that returns a domain type has folded the mapping into the query, so neither can
            be reused or read on its own — and it has taken a decision that belongs one level up,
            where a domain object may be composed from more than one table.
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
                            "Storage method `${fn.name}` returns `$typeName` — server.data may only " +
                                "take/return Row shapes (or primitives/value-class ids/collections), never domain types",
                        )
                    }
                }
        }
    }

    @Describe("A Storage operation that touches only a subset of columns must use a hand-written `update { … it[col] = value … }` block; `setFromRow` writes every column and is wrong here")
    val partialUpdatesByHand by rule { unverifiable() }

    @Describe("A Storage function's name must begin with a declared CRUD verb, so reads and writes are distinguishable by name")
    val crudNaming by rule {
        rationale(
            """
            The rules around this layer distinguish reads from writes, and a call site can only be
            read that way if the function's *name* says which it is. `flowForSession` and `touch` do
            not. A declared verb makes "this call mutates a table" visible at every site that makes
            it, without opening the Storage class.
            """.trimIndent(),
        )
        note("Reads: `get`, `list`, `count`, `observe`. Writes: `insert`, `update`, `upsert`, `delete`, `replace`.")
        note("Plus a closed set of transition verbs for state machines, where a generic write verb loses the meaning — `claimNext` says more than `updateClaimNext`: `claim`, `release`, `reserve`, `reap`, `enqueue`, `heartbeat`, `succeed`, `fail`, `grant`, `revoke`. Adding to that list is a deliberate edit to this rule.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.functions()
                .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
                .filterNot { it.hasOverrideModifier }
                .filterNot { fn -> storageVerbs.any { fn.name.startsWith(it) } }
                .map { Violation(it, "Storage function `${cls.name}.${it.name}` does not begin with a declared CRUD verb") }
        }
    }

    @Describe("A StorageClass must not import `server.domain`")
    val noDomainImports by rule {
        rationale(
            """
            This class speaks Rows. Naming a domain type here means the mapping, or the decision
            about which tables make up a domain object, has moved down into a query — and the
            [Repository](serverdata.md#repository) whose job that is has been bypassed. Keeping the
            layer out of `server.domain` entirely is what makes `returnsRowTypesOnly` hold at every
            other point of the class, not just its return types.
            """.trimIndent(),
        )
        note("Tested per file, so a mapping function or codec sharing the file is covered by the same import test.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.containingFile.imports
                .filter { it.name.contains(".server.domain.") }
                .map { Violation(cls, "StorageClass file imports domain type `${it.name}` — return a Row and let a Repository map it") }
        }
    }

    @Describe("A StorageClass must not inject another StorageClass")
    val doesNotInjectStorage by rule {
        rationale("Ownership runs from the table, and one Storage class reaching into another is how a table acquires a second writer: the reaching class writes rows it does not own, through a path the owner's invariants do not cover. A class that needs tables it does not own is composing, and composing belongs to the Repository above them.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { it.type.name.endsWith("Storage") || it.type.name.endsWith("Store") }
                .map { Violation(cls, "StorageClass injects another StorageClass `${it.type.name}`") }
        }
    }

    @Describe("A StorageClass must not inject a Repository")
    val doesNotInjectRepositories by rule {
        rationale("The Repository is above this class and reads through it; injecting it inverts the layer and puts the mapping back inside the query.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { it.type.name.endsWith("Repository") }
                .map { Violation(cls, "StorageClass injects Repository `${it.type.name}` — the dependency runs the other way") }
        }
    }
}


/** Read, write, and state-transition verb prefixes a Storage function may start with. */
private val storageVerbs = listOf(
    "get", "list", "count", "observe",
    "insert", "update", "upsert", "delete", "replace",
    "claim", "release", "reserve", "reap", "enqueue", "heartbeat", "succeed", "fail", "grant", "revoke",
)

private fun isAllowedStorageReturnTypeName(name: String): Boolean {
    val rowSuffixes = listOf("Row", "Record", "Insert")
    val containerTypes = setOf("List", "Set", "Map", "Flow", "StateFlow", "SharedFlow", "Pair", "Triple")
    val timeTypes = setOf("Instant", "LocalDate", "LocalDateTime", "Duration", "Uuid")

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
