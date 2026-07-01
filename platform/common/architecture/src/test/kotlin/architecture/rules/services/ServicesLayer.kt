package architecture.rules.services

import architecture.registry.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.isMutable
import architecture.definitions.primitiveTypeNames
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider

/**
 * The `services` axis (§3.4, §4.4) — the single, self-contained definition of every services rule.
 *
 * `services` is the cross-the-wire contract (the `@Urpc` interface in `:api`) plus the entire
 * `:server` implementation surface: the `ServiceImpl`, the `services.internal.*` orchestrators /
 * subsystems, and the `services.storage` Postgres persistence. All of these live under the one
 * `feature..services..` package tree, so they are folded into a single rule group here; each
 * construct's package-membership requirement keeps the sub-axes disjoint for the exhaustiveness
 * check (the registry equivalent of the old per-sub-layer `inLayerPackage` gate).
 *
 * A construct owns both its **requirements** (classification — what it means to *be* the construct)
 * and its **rules** (functionality — what the construct must *do*). Layer-level rules that aren't
 * tied to a single construct (the cross-axis import bans and the codegen pipeline) live at the
 * group level.
 *
 * Rule ids are the exact object/property names, e.g. `ServicesLayer.StorageClass.returnsRowTypesOnly`.
 */
object ServicesLayer : RuleGroup(inPackage = "feature..services..") {

    // ---- §4.4.1 Services (the cross-the-wire contract, `:api`) --------------------------------
    object ServiceInterface : Construct(
        // what it is
        isInterfaceWhere("A service is an `interface` annotated `@Urpc`") { decl -> decl.annotations.any { it.name == "Urpc" } },
        hasNameEndingWith("Service"),
        predicate("Resides in the top-level `feature.[name].services` package") { it.isInServicesRoot() },
    ) {
        // what it must do
        val noClientOnlyServices by guidance("Always implement services as urpc service functions in the appropriate server module — do not build client-only local services")
        val plainFunctionShapes by guidance("Functions are plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, each taking 0 or 1 parameter")
        val nestedRequestResponseTypes by guidance("Each function's `Request`/`Response` types are nested `@Serializable` types grouped under a per-function `object` namespace")
        val contractLivesInApi by guidance("Service interfaces live in `feature.[name].services` of the `:api` module")

        val errorsViaExceptions by rule("Service functions propagate errors via thrown exceptions; the return type only ever represents a successful result") {
            rationale(
                """
                @Throws on suspend functions must include CancellationException (or a superclass like
                Exception) — required for Kotlin/Native: kotlinc rejects the function on iOS targets otherwise.
                """.trimIndent(),
            )
            note("Known service exceptions should be their own `@Serializable` type (ideally a `PresentableException`).")
            note("`@Throws` on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException`.")
            constrain { decl, _ ->
                val iface = decl as? KoInterfaceDeclaration ?: return@constrain emptyList()
                iface.functions()
                    .filter { it.hasSuspendModifier }
                    .filter { fn -> fn.hasAnnotation { it.name == "Throws" } }
                    .filterNot { fn ->
                        val text = fn.annotations.first { it.name == "Throws" }.text
                        text.contains("CancellationException::class") ||
                            Regex("""(?<!\w)Exception::class""").containsMatchIn(text)
                    }
                    .map { Violation(it, "@Throws on a suspend service function must include CancellationException") }
            }
        }
    }

    // ---- §4.4.2 Service implementations (`:server`) -------------------------------------------
    object ServiceImpl : Construct(
        isClassWhere("For a service named `[Name]Service` the implementation is a class named `[Name]ServiceImpl`") { it.name.endsWith("ServiceImpl") },
        predicate("Resides in `feature.[name].services` of the `:server` module (dual-life with the contract)") { it.isInServicesRoot() },
    ) {
        val internalVisibility by rule("Service implementations must be `internal`") {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Service implementation must be `internal`"))
            }
        }

        val noInjectingDomainInterfaces by guidance("Service implementations are forbidden from injecting domain interfaces") {
            rationale(
                """
                A ServiceImpl is the server-side request handler; it reaches *down* into services.storage
                and services.internal, not sideways into the domain interfaces a client would consume.
                """.trimIndent(),
            )
            note("Surfaced as guidance rather than a construct requirement: forbidding domain-interface injection is a prohibition, not a classification shape, and re-expressing it would require resolving the domain-interface classifier from another layer.")
        }
        val mayInjectStorageAndInternal by guidance("May inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api`")

        val noUiDependency by rule("Service implementations must not depend on the `ui` package") {
            rationale(
                """
                ServiceImpls run on the server and have no Compose runtime — a UI import here would
                either fail to compile in `:server` or mean a UI type has been pulled out of `ui` and
                is being treated as data, both of which are wrong (§4.4.2, §3.4.4). If you need a
                shared shape with the UI, put it in the feature's `:api` domain or services package.
                """.trimIndent(),
            )
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                if (cls.containingFile.imports.any { it.name.containsPackageSegment("ui") }) {
                    listOf(Violation(decl, "service implementation imports the `ui` package"))
                } else {
                    emptyList()
                }
            }
        }
    }

    // ---- §4.4.3 `services.internal` package (`:server`) ---------------------------------------
    object InternalCoordinator : Construct(
        isClassWhere("A coordinator is a concrete (non-`abstract`, non-`data`) class that is not a `Job` or `Exception`") { decl ->
            !decl.hasAbstractModifier &&
                !decl.hasDataModifier &&
                !decl.name.endsWith("Job") &&
                !decl.name.endsWith("Exception")
        },
        predicate("Resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    )

    object InternalDataCarrier : Construct(
        isClassWhere("A data carrier is a `data class` payload that flows between subsystems through the orchestrator") { it.hasDataModifier },
        predicate("Resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    )

    object InternalInterface : Construct(
        isInterface,
        predicate("Resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    )

    object InternalException : Construct(
        isClassWhere("An internal exception is a class named `[Name]Exception`, thrown only by internal helpers") { it.name.endsWith("Exception") },
        predicate("Resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    )

    object InternalObjectHelper : Construct(
        isObject,
        predicate("Resides in `feature.[name].services.internal`") { it.isInServicesSubAxis("internal") },
    )

    // ---- §4.4.4 `services.storage` package (`:server`) ----------------------------------------
    object StorageClass : Construct(
        isClassWhere("Named `[Name]Storage` (or `[Name]Store` where the broader name fits)") { it.name.endsWith("Storage") || it.name.endsWith("Store") },
        isClassWhere("Not abstract, not a `data class`") { !it.hasAbstractModifier && !it.hasDataModifier },
        predicate("Resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    ) {
        val internalVisibility by rule("Storage classes must be `internal`") {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Storage class must be `internal`"))
            }
        }

        val returnsRowTypesOnly by rule("Storage classes must take/return `XxxRow` types only — never domain types") {
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

        val partialUpdatesByHand by guidance("When an operation touches only a subset of columns, keep the hand-written `update { … it[col] = value … }` block — `setFromRow` writes every column and is wrong here")
    }

    object StorageRecord : Construct(
        isClassWhere("Is a `data class`") { it.hasDataModifier },
        oneOf(hasNameEndingWith("Row"), hasNameEndingWith("Record"), hasNameEndingWith("Insert")),
        predicate("Resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    )

    object MappingFunction : Construct(
        isFunction,
        predicate("Resides in `feature.[name].services.storage`") { it.isInServicesSubAxis("storage") },
    ) {
        val mappersInStorage by guidance("Conversions between a generated `XxxRow` and a domain type live in `services.storage` as plain `internal fun` declarations, conventionally collected in `[Name]Mappers.kt`")
        val multiTableLoadHelpers by guidance("Where storage operations span multiple tables to assemble a richer record, define those higher-level helpers as `suspend fun [Name]Storage.loadXxx(…)` extensions in `services.storage`")
    }

    object CodecObject : Construct(
        isObject,
        predicate("Lives in `services.storage` alongside the Row + mapping functions for the table that uses it") { it.isInServicesSubAxis("storage") },
    ) {
        val keyedToColumn by guidance("Codecs encapsulate the read/write asymmetry `setFromRow` can't express — keep them small and keyed to the column they serve")
    }

    // §4.4.5 `services.tools` is intentionally empty (reserved for AI tool-use subclasses), so it
    // defines no construct: any declaration placed there fails the exhaustiveness check until an
    // `assistantTool` construct is reintroduced. Its isolation rule lives at the layer level below.

    // ---- §3.4.4 cross-axis dependency rules (layer-level — not tied to one construct) ---------
    val mustNotDependOnData by rule("`services` may depend on `domain` and on other features' `:api` `services` contracts; it must not depend on `data`") {
        rationale(
            """
            The server has no `data` layer, and the client's `data` depends on `services`, not the other
            way around. Reaching into client-only `data.storage` (Keychain, SharedPrefs) from a services
            file would fail at runtime or break the client/server split.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.containsPackageSegment("services") == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { it.name.matches(Regex("""feature\.[^.]+\.data\.storage\..+""")) }
                }
                .map { Violation(it.path, "services file imports client-only `data.storage`") }
        }
    }

    val crossFeatureViaApi by rule("May depend on another feature's `services` only via that feature's `:api` module") {
        enforcedBy("ModuleRules.clientApiOnly", "ModuleRules.serverApiOnly")
    }

    val internalHierarchicalVisibility by rule("A class in `services.internal.<subsystem>.**` may not import from a different subsystem under `services.internal` (ancestor data-shape imports are allowed)") {
        rationale(
            """
            Each direct child of `services.internal` is a sealed island. You can see your children freely,
            your parents only for shared data shapes, and never your siblings — cross-subsystem composition
            belongs to the orchestrator at bare `services.internal`, with shared payloads threaded through
            types that live at a common ancestor.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            val ownFeatureInternalRegex = Regex("""^feature\.([^.]+)\.services\.internal(?:\.(.+))?$""")
            val internalImportRegex = Regex("""^feature\.([^.]+)\.services\.internal(?:\.(.+))?\.[^.]+$""")

            // Index project declarations by fully-qualified name so we can resolve each violating
            // import to the actual class/object definition and inspect its modifiers.
            val declarationsByFqn: Map<String, KoBaseDeclaration> =
                scope.declarations()
                    .filterIsInstance<KoFullyQualifiedNameProvider>()
                    .mapNotNull { decl ->
                        val fqn = decl.fullyQualifiedName ?: return@mapNotNull null
                        fqn to (decl as KoBaseDeclaration)
                    }
                    .toMap()

            fun isDataShape(importName: String): Boolean {
                val target = declarationsByFqn[importName] ?: return false
                return when (target) {
                    is KoClassDeclaration ->
                        target.hasDataModifier ||
                            target.hasEnumModifier ||
                            target.hasValueModifier ||
                            target.hasSealedModifier
                    is KoInterfaceDeclaration ->
                        // Only sealed interfaces count — regular interfaces are behaviour contracts.
                        target.hasSealedModifier
                    is KoObjectDeclaration ->
                        // Allow `data object` and any object that's purely a constants holder.
                        target.hasDataModifier ||
                            (target.functions().isEmpty() && target.properties().all { it.isVal && !it.isMutable() })
                    else -> false
                }
            }

            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    val pkg = file.packagee?.name ?: return@flatMap emptyList<Violation>()
                    val ownMatch = ownFeatureInternalRegex.matchEntire(pkg) ?: return@flatMap emptyList<Violation>()
                    val ownFeature = ownMatch.groupValues[1]
                    val ownSubpath = ownMatch.groupValues[2] // "" for bare services.internal

                    file.imports
                        .filter { import ->
                            val importMatch = internalImportRegex.matchEntire(import.name)
                                ?: return@filter false
                            val importFeature = importMatch.groupValues[1]
                            if (importFeature != ownFeature) return@filter false
                            val importSubpath = importMatch.groupValues[2] // "" for bare services.internal

                            val isSameOrDescendant = ownSubpath.isEmpty() ||
                                importSubpath == ownSubpath ||
                                importSubpath.startsWith("$ownSubpath.")
                            if (isSameOrDescendant) return@filter false

                            // Ancestor: importSubpath empty (bare services.internal), or ownSubpath
                            // strictly extends importSubpath with a `.`.
                            val isAncestor = importSubpath.isEmpty() ||
                                ownSubpath.startsWith("$importSubpath.")
                            if (isAncestor) {
                                // Allowed iff the imported declaration is a data shape.
                                return@filter !isDataShape(import.name)
                            }
                            // Otherwise it's lateral / cousin — forbidden outright.
                            true
                        }
                        .map {
                            Violation(
                                file.path,
                                "services.internal file imports `${it.name}` across a subsystem boundary " +
                                    "(lateral/cousin, or a non-data ancestor) — forbidden by hierarchical visibility",
                            )
                        }
                }
        }
    }

    val storageMustNotDependOnInternal by rule("Files in `services.storage` must not import from `services.internal` — the dependency direction inside `services` is `internal → storage`") {
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.matches(Regex("""feature\.[^.]+\.services\.storage(\..+)?""")) == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { it.name.matches(Regex("""feature\.[^.]+\.services\.internal\..+""")) }
                }
                .map { Violation(it.path, "services.storage file imports from services.internal") }
        }
    }

    val toolsApiContractOnly by rule("Anything placed in `services.tools` may depend on the Service contract via `:api`-defined types only — never on `services.storage` or `services.internal`") {
        rationale(
            """
            Tools are AI-callable wrappers around the Service contract — they should consume the `:api`
            Service interface only, not reach into Postgres tables or internal orchestrators directly. The
            isolation rule is enforced now even though the package is empty.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.matches(Regex("""feature\.[^.]+\.services\.tools(\..+)?""")) == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import ->
                        import.name.matches(Regex("""feature\.[^.]+\.services\.storage\..+""")) ||
                            import.name.matches(Regex("""feature\.[^.]+\.services\.internal\..+"""))
                    }
                }
                .map { Violation(it.path, "services.tools file imports services.storage/internal — use the `:api` contract only") }
        }
    }

    // ---- §4.4.4.2–§4.4.4.3 Postgres codegen (generated into `platform.server.postgres.tables`) -
    // These describe sources generated by the `dev.isaacudy.udytils.postgres` Gradle plugin; they
    // live in a shared platform package, are never committed, and are not scanned by Konsist — so
    // they are layer-level `codegen` rules, not feature constructs.
    val generatedTableRowSources by rule("`Table`/`Row` sources are generated by the `dev.isaacudy.udytils.postgres` plugin from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables`") { codegen() }
    val generatedTableObjects by rule("Each persisted entity has a generated `object XxxTable : Table(\"xxx\")` (plural); custom columns use the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …)") { codegen() }
    val everyColumnOnTable by rule("Every column on the SQL table is declared on the `Table` object, with no omissions; the UUID primary key is `uuid(\"id\").autoGenerate()` but the write path always supplies the id explicitly") { codegen() }
    val rowDataClassPrimitives by rule("The in-memory persistence shape is a top-level `data class XxxRow` (singular) whose fields use only primitive types — no domain wrappers, enums, or sealed hierarchies") { codegen() }
    val rowFakeConstructorAndSetFromRow by rule("Each generated file exposes a fake-constructor `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes") { codegen() }
}

/**
 * The dotted package sub-path after `…services` for this declaration, or `null` if it isn't in a
 * `services` package. `""` for bare `feature.[name].services`; `"internal.foo"` / `"storage"` etc.
 * for the sub-axes. Guards against false matches like `…servicesRegistry`.
 */
private fun KoBaseDeclaration.servicesSubpath(): String? {
    val pkg = containingFilePackage()
    val idx = pkg.indexOf(".services")
    if (idx < 0) return null
    val after = pkg.substring(idx + ".services".length)
    if (after.isNotEmpty() && !after.startsWith(".")) return null
    return after.removePrefix(".")
}

private fun String.isUnderSegment(segment: String): Boolean = this == segment || startsWith("$segment.")

/** In the top-level `feature.[name].services` package (the contract / ServiceImpl), not a sub-axis. */
private fun KoBaseDeclaration.isInServicesRoot(): Boolean {
    val sub = servicesSubpath() ?: return false
    return !sub.isUnderSegment("internal") && !sub.isUnderSegment("storage") && !sub.isUnderSegment("tools")
}

/** In the named `services` sub-axis (`internal`, `storage`, or `tools`) of any feature. */
private fun KoBaseDeclaration.isInServicesSubAxis(segment: String): Boolean =
    servicesSubpath()?.isUnderSegment(segment) == true

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
