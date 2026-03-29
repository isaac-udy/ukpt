package architecture.utils

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

fun guessFullyQualifiedName(
    file: KoFileDeclaration,
    name: String,
): String {
    val baseName = name.substringBefore("<")

    val isAlreadyFullQualified = baseName.contains(".") && baseName.firstOrNull()?.isLowerCase() == true
    if (isAlreadyFullQualified) {
        return baseName
    }

    val collectionName = collectionTypeNames.firstOrNull { it == baseName }
    if (collectionName != null) {
        return collectionName
            .removePrefix("kotlin.collections.")
            .let { "kotlin.collections.$it" }
    }

    val importMatchedName = file.imports
        .map { it.name }
        .firstOrNull { importName ->
            baseName.split(".").fold("") { acc, s ->
                val name = when {
                    acc.isEmpty() -> s
                    else -> "$acc.$s"
                }
                when {
                    importName.endsWith(name) -> return@firstOrNull true
                    else -> acc
                }
            }
            return@firstOrNull false
        }
    if (importMatchedName != null) return importMatchedName
    return (file.packagee?.name.orEmpty() + "." + baseName)
}
