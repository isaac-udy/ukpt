package architecture.definitions

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoImportDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndInterfaceAndObjectDeclaration
import com.lemonappdev.konsist.api.declaration.type.KoTypeDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoContainingDeclarationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoParentProvider
import com.lemonappdev.konsist.api.provider.modifier.KoModifierProvider

/**
 * True for declarations that live syntactically inside a function body
 * — i.e. local functions, local classes, local properties. Architecture
 * layer scans deliberately ignore these because they are scoped to
 * the surrounding implementation and aren't part of the public layer
 * surface. Declarations inside *classes* (methods, nested types) are
 * **not** treated as locals — those are still tracked.
 */
fun KoBaseDeclaration.isInsideFunction(): Boolean {
    if (this !is KoContainingDeclarationProvider) return false
    return containingDeclaration is KoFunctionDeclaration
}

fun KoBaseDeclaration.containingFilePath(): String {
    return when (this) {
        is KoContainingFileProvider -> this.containingFile.path
        is KoFileDeclaration -> this.path
        else -> null
    }.orEmpty()
}

fun KoBaseDeclaration.containingFilePackage(): String {
    return when (this) {
        is KoContainingFileProvider -> this.containingFile.packagee?.name
        is KoFileDeclaration -> this.packagee?.name
        else -> null
    }.orEmpty()
}

fun KoBaseDeclaration.isAppModule(): Boolean {
    val path = containingFilePath()
    val beforeSrc = path.substringBeforeLast("/src/")
    return beforeSrc.contains("/app/")
}

/**
 * In :feature:core, we sometimes create "platform" code before we're ready to actually move this
 * out into a proper platform module, but we want to apply the platform rules to this code as
 * if it was actually in a platform module, so this function lets us check whether
 * the KoBaseDeclaration is a "temporary :feature:core platform"
 */
private fun KoBaseDeclaration.isTemporaryFeatureCorePlatform(): Boolean {
    val path = containingFilePath()
    val beforeSrc = path.substringBeforeLast("/src/")
    val isFeatureCore = beforeSrc.contains("/feature/core/")
    if (!isFeatureCore) return false
    val pkg = containingFilePackage()
    return pkg.startsWith("platform.")
}

fun KoBaseDeclaration.isFeatureModule(): Boolean {
    val path = containingFilePath()
    val beforeSrc = path.substringBeforeLast("/src/")
    val pkg = containingFilePackage()
    return beforeSrc.contains("/feature/") && !isTemporaryFeatureCorePlatform()
}

fun KoBaseDeclaration.isPlatformModule(): Boolean {
    val path = containingFilePath()
    val beforeSrc = path.substringBeforeLast("/src/")
    val pkg = containingFilePackage()
    return beforeSrc.contains("/platform/") || isTemporaryFeatureCorePlatform()
}

fun KoBaseDeclaration.isClientModule(): Boolean {
    val path = containingFilePath()
    val beforeSrc = path.substringBeforeLast("/src/")
    return beforeSrc.endsWith("/client")
}

fun KoBaseDeclaration.isServerModule(): Boolean {
    val path = containingFilePath()
    val beforeSrc = path.substringBeforeLast("/src/")
    return beforeSrc.endsWith("/server")
}

fun KoBaseDeclaration.isApiModule(): Boolean {
    val path = containingFilePath()
    val beforeSrc = path.substringBeforeLast("/src/")
    return beforeSrc.endsWith("/api")
}

/**
 * Returns the name of the feature associated with the KoBaseDeclaration, which will be the name
 * of the containing file/package's feature name unless the KoBaseDeclaration is an *import*,
 * in which case will be the feature name of whatever the KoImportDeclaration is importing.
 */
fun KoBaseDeclaration.featureName(): String {
    if (this is KoImportDeclaration) {
        return featureNameFromImportName()
    }

    val fromFile = featureNameFromContainingFile().takeIf { it.isNotBlank() }
    val fromPackage = featureNameFromContainingPackage().takeIf { it.isNotBlank() }

    if (fromPackage == null) return fromFile.orEmpty()
    if (fromFile == null) return fromPackage

    // The ":feature:core" module is essentially a placeholder module before proper feature modules
    // are created, which means that if a file is in ":feature:core", we care about the package
    // declaration rather than the file path.
    // If a file is not in the ":feature:core" package, we expect the fromFile and fromPackage
    // feature names to align
    if (fromFile == "core") return fromPackage
    return fromFile.takeIf { it == fromPackage }.orEmpty()
}

fun KoBaseDeclaration.featureNameFromContainingFile(): String {
    val path = containingFilePath()
    val beforeSrc = path.substringBeforeLast("/src/")
    val feature = beforeSrc.substringAfterLast("/feature/")
    return feature.substringBefore("/")
        .takeIf { it.isNotBlank() }
        .orEmpty()
}

fun KoBaseDeclaration.featureNameFromContainingPackage(): String {
    if (!isFeatureModule()) return "<not a feature module>"
    return featureNameFromRawPackage(containingFilePackage())
}

fun KoImportDeclaration.featureNameFromImportName(): String {
    return featureNameFromRawPackage(name)
}

/**
 * The feature a package belongs to: the first segment after `feature.`. Everything deeper — a side,
 * a layer, a sub-package — is internal structure of that same feature, so
 * `feature.entities` and `feature.entities.client.data` both name the feature `entities`.
 */
private fun featureNameFromRawPackage(
    pkg: String
): String = pkg.substringAfter("feature.").substringBefore(".")

fun KoBaseDeclaration.isKotlinxSerializable(): Boolean {
    return when (this) {
        is KoAnnotationProvider -> this.hasAnnotation {
            it.fullyQualifiedName == "kotlinx.serialization.Serializable"
        }

        else -> false
    }
}

fun KoBaseDeclaration.toDebugString(): String {
    val kind = when (this) {
        is KoClassDeclaration -> "class"
        is KoInterfaceDeclaration -> "interface"
        is KoObjectDeclaration -> "object"
        is KoFunctionDeclaration -> "function"
        is KoPropertyDeclaration -> "property"
        is KoFileDeclaration -> "file"
        else -> "declaration"
    }

    val qualifiedName = (this as? KoFullyQualifiedNameProvider)?.fullyQualifiedName
    val name = (qualifiedName ?: (this as? KoNameProvider)?.name ?: toString())
        .removePrefix(containingFilePackage())
        .removePrefix(".")

    val path = containingFilePath()
    return "$kind $name" + if (path.isNotBlank()) " (file://$path)" else ""
}

fun KoBaseDeclaration.isPrivate(): Boolean {
    return when (this) {
        is KoModifierProvider -> this.hasModifier(KoModifier.PRIVATE)
        else -> false
    }
}

/**
 * Checks whether the given string contains a package segment matching [segment].
 * For example, `containsPackageSegment("data")` matches `feature.auth.data.storage`
 * and `feature.auth.data`, but NOT `feature.auth.database`.
 */
internal fun String.containsPackageSegment(segment: String): Boolean {
    return Regex("""\.$segment(\.|$)""").containsMatchIn(this)
}

internal val primitiveTypeNames = setOf(
    "String", "kotlin.String",
    "Int", "kotlin.Int",
    "Long", "kotlin.Long",
    "Double", "kotlin.Double",
    "Float", "kotlin.Float",
    "Boolean", "kotlin.Boolean",
    "Byte", "kotlin.Byte",
    "Short", "kotlin.Short",
    "Char", "kotlin.Char",
    "UInt", "kotlin.UInt",
    "ULong", "kotlin.ULong",
    "UByte", "kotlin.UByte",
    "UShort", "kotlin.UShort",
    "Unit", "kotlin.Unit",
    "Nothing", "kotlin.Nothing",
)

fun KoBaseDeclaration.isPrimitiveType(): Boolean {
    val typeName = when (this) {
        is KoTypeDeclaration -> this.name
        is KoParameterDeclaration -> this.type.name
        is KoNameProvider -> this.name
        else -> return false
    }
    val baseName = typeName
        .replace("?", "")
        .substringBefore("<")

    return baseName in primitiveTypeNames
}

private val mutableTypes = listOf(
    // Compose
    "androidx.compose.runtime.MutableState",

    // Flows
    "kotlinx.coroutines.flow.MutableSharedFlow",
    "kotlinx.coroutines.flow.MutableStateFlow",

    // Collections
    "kotlin.collections.MutableList",
    "kotlin.collections.MutableMap",
    "kotlin.collections.MutableSet",
    "kotlin.collections.MutableCollection",
    "kotlin.collections.MutableIterable",
    "kotlin.collections.MutableIterator",
)

fun KoBaseDeclaration.isMutable(): Boolean {
    when (this) {
        is KoPropertyDeclaration -> {
            if (isVar) return true
            val type = type ?: return text
                .substringAfter("=")
                .contains("Mutable")
            return type.isMutableType
        }
        is KoTypeDeclaration -> {
            if (isMutableType) return true
            val source = sourceDeclaration ?: return false
            return source.isMutable()
        }
        is KoClassAndInterfaceAndObjectDeclaration -> {
            val parentsAreMutable = parents()
                .mapNotNull { it.sourceDeclaration }
                .any { it.isMutable() }
            if (parentsAreMutable) return true
            return fullyQualifiedName in mutableTypes
        }
        else -> return false
    }
}


// ---- the feature taxonomy (see platform/common/architecture/README.md) --------------------------
// A feature package is `feature.<name>[.<side>.<layer>[.<sub>]]`. The feature root is the two-segment
// form and holds the shared wire vocabulary; everything deeper is side-private.

/** `feature.campaigns` → true; `feature.campaigns.client.ui` → false. */
fun KoBaseDeclaration.isFeatureRootPackage(): Boolean {
    val pkg = containingFilePackage()
    if (!pkg.startsWith("feature.")) return false
    return !pkg.removePrefix("feature.").contains('.')
}

/** "client" / "server" for a side package; null for the feature root. */
fun KoBaseDeclaration.featureSide(): String? {
    val segments = containingFilePackage().removePrefix("feature.").split('.')
    return segments.getOrNull(1)?.takeIf { it == "client" || it == "server" }
}

/** True once this declaration's package carries a `client.`/`server.` segment. */
fun KoBaseDeclaration.isSideFirstPackage(): Boolean = featureSide() != null

/**
 * True for a declaration in `feature.[name].server.data` — the package the ServerData Constructs
 * classify. The group's `inPackage` gate is the same package, so a Construct can never reach wider
 * than this; the layer's file-based import rules (`ServerData.noServiceImports`,
 * `ServerData.noClientImports`, `ServerData.tableAccessOwnedByStorage`) use `isInServerData()`,
 * the file-level form of the same test.
 */
fun KoBaseDeclaration.isInServerDataPackage(): Boolean = containingFilePackage().contains(".server.data")

/**
 * True for a declaration in `feature.[name].server.data.storage` — the Row-speaking subpackage that
 * holds the layer's Storage classes and hand-written storage records, mirroring `client.data.storage`.
 * The rest of `server.data` — Repositories, mapping functions, codec objects, IntegrationClients —
 * names domain types, and sits at the layer's root instead.
 *
 * Anchored to the layer root deliberately: storage is a feature's single flat persistence surface,
 * visible layer-wide, which is what gives a table one owning StorageClass. A `storage` segment
 * nested under a subsystem package would be a second surface wearing the same name.
 */
fun KoBaseDeclaration.isInServerDataStoragePackage(): Boolean =
    serverDataStoragePackageRegex.matches(containingFilePackage())

// Exactly the flat package, no descendants: one flat surface is what gives a table one owning
// StorageClass, so a declaration in a package nested under `storage` classifies as nothing and
// the exhaustiveness rule reports it.
private val serverDataStoragePackageRegex = Regex("""^feature\.[^.]+\.server\.data\.storage$""")

/**
 * True when a `@Serializable` declaration's discriminator is written into a payload — i.e. it is
 * dispatched polymorphically, so an absent `@SerialName` means its fully-qualified name becomes
 * part of the serialized format.
 *
 * Two shapes qualify:
 *  - a **variant of a sealed hierarchy**, which kotlinx always discriminates; and
 *  - an Enro **`NavigationKey`**, which `SerializerRepository` registers for polymorphic dispatch
 *    and `WebHistoryPlugin` serializes into `window.history.state`.
 *
 * The second is the one a sealed-variant-only check misses, and it is why this rule exists in this
 * broader form. Other polymorphic registrations are invisible to static analysis; if one is added,
 * extend this predicate rather than exempting the declarations.
 */
fun KoBaseDeclaration.participatesInPolymorphicSerialization(): Boolean =
    isNavigationKey() || sealedParentSimpleNames().isNotEmpty()

/**
 * True for an Enro navigation destination — a declaration implementing `NavigationKey` or one of its
 * nested forms, such as `NavigationKey.WithResult<T>`.
 */
fun KoBaseDeclaration.isNavigationKey(): Boolean =
    (this as? KoParentProvider)?.parents().orEmpty()
        .any { it.name == "NavigationKey" || it.name.startsWith("NavigationKey.") }

/**
 * The simple names of the `sealed` types this declaration is a variant of — empty when it is not a
 * sealed variant. A declaration reaches more than one only by implementing two sealed interfaces.
 */
fun KoBaseDeclaration.sealedParentSimpleNames(): List<String> =
    (this as? KoParentProvider)?.parents().orEmpty()
        .filter { it.namesSealedType(this) }
        .map { it.name.substringAfterLast('.') }

/**
 * The type-nesting chain from the outermost declaring type down to this declaration, inclusive —
 * `["FooDestination", "Action", "Delete"]` for a variant nested two deep, or just the declaration's
 * own name at the top level. Package-free by construction, which is what makes it the identity a
 * discriminator pins: it moves with the class when the package changes.
 */
fun KoBaseDeclaration.typeNestingChain(): List<String> {
    val names = mutableListOf<String>()
    var current: KoBaseDeclaration? = this
    while (current is KoClassDeclaration || current is KoInterfaceDeclaration || current is KoObjectDeclaration) {
        val name = (current as? KoNameProvider)?.name ?: break
        names += name
        current = (current as? KoContainingDeclarationProvider)?.containingDeclaration as? KoBaseDeclaration
    }
    return names.reversed()
}

/**
 * The string a declaration pins its discriminator to, or null when it pins none.
 */
fun KoBaseDeclaration.serialNameValue(): String? =
    (this as? KoAnnotationProvider)?.annotations
        ?.firstOrNull { it.name == "SerialName" }
        ?.text
        ?.let { Regex("""\"([^\"]*)\"""").find(it) }
        ?.groupValues?.get(1)

/**
 * Whether a parent entry names a `sealed` type, making the declaring child a variant of it.
 *
 * Konsist's `sourceDeclaration` only resolves a parent that is declared at the top level of a file.
 * A parent nested inside another type — `StoredSession.ProcessingStatus`, `AuthCredentials.
 * VerificationResult`, and every `WithResult` destination's nested `Action`/`Result` — resolves to
 * nothing, so the modifier check alone silently returns false for the entire hierarchy and reports
 * it as conformant. The fallback re-resolves the parent against the declaration tree of the file
 * the child sits in, which is where a nested sealed root always is: Kotlin requires a sealed
 * hierarchy to share the root's package and compilation unit, and a nested root additionally
 * confines its variants to the root's own body.
 *
 * Matching is on the parent's trailing name segment because a variant may name its root either bare
 * (`: ProcessingStatus`) or qualified (`: AuthCredentials.VerificationResult`).
 */
private fun KoParentDeclaration.namesSealedType(child: KoBaseDeclaration): Boolean {
    val resolved = sourceDeclaration as? KoBaseDeclaration
    if ((resolved as? KoModifierProvider)?.isSealed() == true) return true

    val parentName = name.substringAfterLast('.')
    val containingFile = (child as? KoContainingFileProvider)?.containingFile ?: return false
    return containingFile
        .classesAndInterfacesAndObjects(includeNested = true)
        .any { it.name == parentName && it.isSealed() }
}

private fun KoModifierProvider.isSealed(): Boolean = hasModifier(KoModifier.SEALED)
