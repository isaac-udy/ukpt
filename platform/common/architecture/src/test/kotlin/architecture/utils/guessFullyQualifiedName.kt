package architecture.utils

import architecture.definitions.primitiveTypeNames
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

fun guessFullyQualifiedName(
    file: KoFileDeclaration,
    name: String,
): String {
    val baseName = name.substringBefore("<")

    // We're going to consider a name to already be a fully qualified name if it contains a "."
    // and starts with a lowercase letter. For example 'com.example.Test' would be considered
    // a fully qualified name, because it contains a '.' and 'c' is lowercase,
    // but 'Example.Something' would not be considered fully qualified already
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

    val primitiveName = primitiveTypeNames.firstOrNull { it == baseName }
    if (primitiveName != null) {
        return primitiveName
            .removePrefix("kotlin.")
            .let { "kotlin.$it" }
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
    // If the type has not been imported, it must be in the same package as the
    // file which it is declared in, so we're going to create our own type name
    return (file.packagee?.name.orEmpty() + "." + baseName)
}