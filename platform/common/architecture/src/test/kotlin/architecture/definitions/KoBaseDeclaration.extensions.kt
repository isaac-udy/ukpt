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
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndInterfaceAndObjectDeclaration
import com.lemonappdev.konsist.api.declaration.type.KoTypeDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoContainingDeclarationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
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

private fun featureNameFromRawPackage(
    pkg: String
): String {
    val afterFeature = pkg.substringAfter("feature.")
    return when {
        afterFeature.containsPackageSegment("domain") -> afterFeature.substringBefore(".domain")
        afterFeature.containsPackageSegment("ui") -> afterFeature.substringBefore(".ui")
        afterFeature.containsPackageSegment("data") -> afterFeature.substringBefore(".data")
        afterFeature.containsPackageSegment("services") -> afterFeature.substringBefore(".services")
        else -> afterFeature
    }
}

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

