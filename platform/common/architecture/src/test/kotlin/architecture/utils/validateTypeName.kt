package architecture.utils

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

fun validateTypeName(
    typeName: String,
    declaredIn: KoFileDeclaration,
    predicate: (String) -> Boolean,
) : Boolean {
    val nameWithGenerics = NameWithGenerics.from(typeName)
    val genericsAreValid = nameWithGenerics.generics.all { generic ->
        validateTypeName(generic, declaredIn, predicate)
    }
    if (!genericsAreValid) return false
    val qualifiedName = guessFullyQualifiedName(declaredIn, nameWithGenerics.baseName)
    return predicate(qualifiedName)
}
