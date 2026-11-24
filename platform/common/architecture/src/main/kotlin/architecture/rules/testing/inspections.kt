package architecture.rules.testing

import architecture.definitions.containingFilePackage
import architecture.definitions.withoutStringsAndComments
import architecture.rules.shared.DOMAIN_READ_PREFIXES
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import dev.isaacudy.udytils.architecture.*

internal val hasServerInspectionReceiver = isFunctionWhere("has a `ServerInspection` receiver") { it.receiverType?.name == "ServerInspection" }
internal val hasClientInspectionReceiver = isFunctionWhere("has a `ClientInspection` receiver") { it.receiverType?.name == "ClientInspection" }

internal fun inspectionPlacement() = placement("a `:feature:[name]:tests` module's `src/commonMain/`") { decl ->
    decl.isInFeatureTestsModule() && decl.sourceSet() == "commonMain"
}

internal fun readsOnlyViolations(decl: KoBaseDeclaration, scopes: ArchitectureScopes, layer: String): List<Violation> {
    val code = (decl as KoFunctionDeclaration).text
    return scopes.productionReferences(decl.file)
        .filter { reference -> Regex("""\b${reference.name}\b""").containsMatchIn(code) }
        .mapNotNull { reference ->
            val ids = reference.constructs.map { it.id }
            val isDomainInterface = "$layer.DomainInterface" in ids ||
                ((reference.declaration as? KoInterfaceDeclaration)?.hasFunModifier == true && DOMAIN_PACKAGE.containsMatchIn(reference.declaration.containingFilePackage()))
            when {
                isDomainInterface && DOMAIN_READ_PREFIXES.any { reference.name.startsWith(it) } -> null
                isDomainInterface -> Violation(decl, "references `${reference.name}`, a domain interface not named for a read")
                ids.isEmpty() && LAYER_PACKAGE.containsMatchIn(reference.declaration.containingFilePackage()) ->
                    Violation(decl, "references `${reference.name}`, a class of a feature's data or UI layer")
                ids.isEmpty() || ids.any { it in VALUE_CONSTRUCTS } -> null
                else -> Violation(decl, "references `${reference.name}` (${ids.joinToString()})")
            }
        }
}

internal fun exposedWriteViolations(decl: KoBaseDeclaration): List<Violation> {
    val code = (decl as KoFunctionDeclaration).text.withoutStringsAndComments()
    val writes = EXPOSED_WRITE.findAll(code).map { it.groupValues[1] }.distinct()
        .map { Violation(decl, "calls `$it`, an Exposed write") }
    val sql = if (INSPECTION_SQL.containsMatchIn(decl.text)) listOf(Violation(decl, "contains SQL")) else emptyList()
    return writes.toList() + sql
}

private val EXPOSED_WRITE = Regex("""\b(insert\w*|batchInsert|upsert\w*|batchUpsert|replace|update\w*|delete\w*|exec\w*|truncate\w*)\s*[({]""")

private val INSPECTION_SQL = Regex("""\b(INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET|SELECT\s+.+?\s+FROM|TRUNCATE)\b""", RegexOption.IGNORE_CASE)

private val DOMAIN_PACKAGE = Regex("""\.(client|server)\.domain(\.|$)""")

private val LAYER_PACKAGE = Regex("""\.(client|server)\.(data|ui|services)(\.|$)""")

internal val DIRECT_DATABASE_IMPORTS = listOf(
    "java.sql.",
    "javax.sql.",
    "org.jetbrains.exposed.v1.jdbc.transactions.",
    "dev.isaacudy.udytils.postgres.",
)
