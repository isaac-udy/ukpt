package architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

object ArchitectureExceptions {
    val classes = listOf<String>(
    )

    val functions = listOf<String>(
    )

    fun isIgnored(declaration: KoBaseDeclaration): Boolean {
        return when (declaration) {
            is KoClassDeclaration -> declaration.fullyQualifiedName in classes
            is KoFunctionDeclaration -> declaration.fullyQualifiedName in functions
            else -> false
        }
    }
}
