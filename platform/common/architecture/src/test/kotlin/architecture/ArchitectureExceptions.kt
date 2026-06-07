package architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider

/**
 * Architecture exceptions are now declared via the
 * [architecture.ArchitectureException] annotation directly on the offending
 * declaration (or on the file containing it) rather than in a string list
 * here. See `:platform:common:architecture/src/main/kotlin/architecture/ArchitectureException.kt`.
 *
 * The helpers in this file read the annotation so individual rule tests can
 * filter out exempt declarations. Each helper takes a list of rule IDs and
 * returns true if the declaration (or its containing file) carries an
 * `@ArchitectureException` listing any of those rule IDs.
 */
object ArchitectureExceptions {

    /**
     * True if [declaration] carries an `@ArchitectureException` exempting it
     * from any of [ruleIds], either directly on the declaration or on its
     * containing file.
     */
    fun isExempt(declaration: KoBaseDeclaration, vararg ruleIds: String): Boolean {
        val target = ruleIds.toSet()
        // Declaration-level annotation
        if (declaration is KoAnnotationProvider) {
            if (declaration.exemptsAny(target)) return true
        }
        // File-level annotation
        if (declaration is KoContainingFileProvider) {
            if (declaration.containingFile.exemptsAny(target)) return true
        }
        return false
    }

    /**
     * True if the file carries an `@file:ArchitectureException` exempting it
     * from any of [ruleIds].
     */
    fun isFileExempt(file: KoFileDeclaration, vararg ruleIds: String): Boolean {
        return file.exemptsAny(ruleIds.toSet())
    }

    private fun KoAnnotationProvider.exemptsAny(target: Set<String>): Boolean {
        val exemption = annotations.firstOrNull { it.name == "ArchitectureException" }
            ?: return false
        // Konsist exposes annotation arguments as raw text. Pull `ruleIds`
        // from the annotation source via a regex on the annotation text.
        val text = exemption.text
        // Capture the ruleIds = [...] argument and extract quoted IDs.
        val match = ARG_REGEX.find(text) ?: return false
        val list = match.groupValues[1]
        val ids = ID_REGEX.findAll(list).map { it.groupValues[1] }.toSet()
        return ids.any { it in target }
    }

    private val ARG_REGEX = Regex("""ruleIds\s*=\s*\[([^\]]*)\]""")
    private val ID_REGEX = Regex("""["']([^"']+)["']""")

    /**
     * Legacy class-list exemption hook. Retained for the
     * `validateAllDeclarationsBelongToDefinedLayer` meta-test, which exempts
     * a small set of transitional helper classes that aren't yet classifiable
     * into any layer construct. Migrating these to annotations would require
     * a layer-construct id (which doesn't exist yet); that's a follow-up.
     */
    fun isIgnored(declaration: KoBaseDeclaration): Boolean {
        // Annotation-based exemption for the layer-membership meta test.
        if (declaration is KoAnnotationProvider) {
            if (declaration.exemptsAny(setOf("LAYER-MEMBERSHIP"))) return true
        }
        // Empty by default — keep narrow.
        val classes = emptyList<String>()
        return when (declaration) {
            is KoClassDeclaration -> declaration.fullyQualifiedName in classes
            is KoInterfaceDeclaration -> declaration.fullyQualifiedName in classes
            is KoObjectDeclaration -> declaration.fullyQualifiedName in classes
            is KoFunctionDeclaration -> declaration.fullyQualifiedName in classes
            else -> false
        }
    }
}
