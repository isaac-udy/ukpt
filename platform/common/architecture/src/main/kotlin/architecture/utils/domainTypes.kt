package architecture.utils

import architecture.definitions.primitiveTypeNames
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * Standard date/time types are domain-appropriate value types (a `Session.date` is an `Instant`),
 * so a domain interface may name them directly even though they are neither primitives nor feature
 * types. Matches both the short name and the fully-qualified forms `validateTypeName` resolves to.
 */
private val domainTimeTypeNames = setOf(
    "Instant", "kotlin.time.Instant", "kotlinx.datetime.Instant",
    "LocalDate", "kotlinx.datetime.LocalDate",
    "LocalDateTime", "kotlinx.datetime.LocalDateTime",
    "LocalTime", "kotlinx.datetime.LocalTime",
    "Duration", "kotlin.time.Duration",
    "DatePeriod", "kotlinx.datetime.DatePeriod",
    "DateTimePeriod", "kotlinx.datetime.DateTimePeriod",
)

/**
 * A feature type a domain declaration may name: a shared domain model in a feature root
 * (`feature.campaigns.CampaignId`), or a side-private domain model
 * (`feature.campaigns.client.domain.DraftState`, `feature.campaigns.server.domain.Ledger`).
 */
private fun isFeatureDomainType(name: String): Boolean {
    if (!name.startsWith("feature.")) return false
    if (name.contains(".client.domain.") || name.contains(".server.domain.")) return true
    // Feature root: `feature.<name>.<Type>` — exactly one segment between the prefix and the type.
    val rest = name.removePrefix("feature.").split('.')
    return rest.size == 2 && rest[1].firstOrNull()?.isUpperCase() == true
}

/**
 * A type is domain-compatible if it (and its generics) are primitives, collections, reactive
 * wrappers (`Flow`/`StateFlow`/`SharedFlow`), standard date/time value types, platform types, or
 * feature domain types. The wrapper base name is allowed but its type argument is still validated,
 * so `Flow<Session?>` passes while `Flow<android.view.View>` does not.
 */
fun isDomainCompatibleType(typeName: String, declaredIn: KoFileDeclaration): Boolean =
    validateTypeName(typeName, declaredIn) {
        it in primitiveTypeNames || it in collectionTypeNames || it in reactiveWrapperTypeNames ||
            it in domainTimeTypeNames || it.startsWith("platform.") || isFeatureDomainType(it)
    }

/**
 * The package the Postgres codegen writes its Exposed `Table` objects and `Row` types into
 * (`:platform:server:postgres`, `outputPackage`). Naming one of these is naming a column, so it is
 * a persistence dependency even though the package reads as ordinary `platform.` code.
 */
fun String.isGeneratedTableImport(): Boolean = startsWith("platform.server.postgres.tables.")

/** Imports that make a package non-pure: Android, Compose, Ktor, SQL, persistence. */
fun String.isPlatformSpecificImport(): Boolean =
    startsWith("android.") || startsWith("androidx.") || startsWith("io.ktor.") ||
        contains(".sql.") || contains("sqldelight") || contains("org.jetbrains.exposed") ||
        contains("room") || isGeneratedTableImport()
