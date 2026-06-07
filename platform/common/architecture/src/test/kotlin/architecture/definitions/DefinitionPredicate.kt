package architecture.definitions

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndInterfaceAndObjectDeclaration
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoDeclarationCastProvider
import com.lemonappdev.konsist.api.provider.KoIsTopLevelProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoParentProvider
import com.lemonappdev.konsist.api.provider.modifier.KoModifierProvider

sealed interface DefinitionPredicate<out Type : KoBaseDeclaration> {
    fun test(declaration: KoBaseDeclaration?): Boolean

    fun test(declaration: KoDeclarationCastProvider?): Boolean {
        if (declaration !is KoBaseDeclaration) return false
        return test(declaration as KoBaseDeclaration)
    }

    fun or(other: DefinitionPredicate<KoBaseDeclaration>): DefinitionPredicate<KoBaseDeclaration> {
        return anyOf(this, other)
    }

    fun and(other: DefinitionPredicate<KoBaseDeclaration>): DefinitionPredicate<KoBaseDeclaration> {
        return allOf(this, other)
    }

    companion object {
        fun any(
            block: (KoBaseDeclaration) -> Boolean,
        ) : DefinitionPredicate<KoBaseDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration },
                testPredicate = block,
            )
        }

        fun anyOf(
            vararg predicates: DefinitionPredicate<*>,
        ): DefinitionPredicate<KoBaseDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration },
                testPredicate = { declaration -> predicates.any { it.test(declaration) } },
            )
        }

        fun allOf(
            vararg predicates: DefinitionPredicate<*>,
        ): DefinitionPredicate<KoBaseDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration },
                testPredicate = { declaration -> predicates.all { it.test(declaration) } },
            )
        }

        fun classOrInterfaceOrObject(
            block: (KoClassAndInterfaceAndObjectDeclaration) -> Boolean,
        ): DefinitionPredicate<KoClassAndInterfaceAndObjectDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoClassAndInterfaceAndObjectDeclaration },
                testPredicate = block,
            )
        }

        fun classOrObject(
            block: (KoClassAndObjectDeclaration) -> Boolean,
        ): DefinitionPredicate<KoClassAndObjectDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoClassAndObjectDeclaration },
                testPredicate = block,
            )
        }

        fun classOrInterface(
            block: (KoClassAndInterfaceDeclaration) -> Boolean,
        ): DefinitionPredicate<KoClassAndInterfaceDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoClassAndInterfaceDeclaration },
                testPredicate = block,
            )
        }

        fun cls(
            block: (KoClassDeclaration) -> Boolean,
        ): DefinitionPredicate<KoClassDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoClassDeclaration },
                testPredicate = block,
            )
        }

        fun obj(
            block: (KoObjectDeclaration) -> Boolean,
        ): DefinitionPredicate<KoObjectDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoObjectDeclaration },
                testPredicate = block,
            )
        }

        fun iface(
            block: (KoInterfaceDeclaration) -> Boolean,
        ): DefinitionPredicate<KoInterfaceDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoInterfaceDeclaration },
                testPredicate = block,
            )
        }

        fun property(
            block: (KoPropertyDeclaration) -> Boolean,
        ): DefinitionPredicate<KoPropertyDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoPropertyDeclaration },
                testPredicate = block,
            )
        }

        fun function(
            block: (KoFunctionDeclaration) -> Boolean,
        ): DefinitionPredicate<KoFunctionDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoFunctionDeclaration },
                testPredicate = block,
            )
        }

        fun file(
            block: (KoFileDeclaration) -> Boolean,
        ): DefinitionPredicate<KoFileDeclaration> {
            return DefinitionPredicateImpl(
                asType = { declaration -> declaration as? KoFileDeclaration },
                testPredicate = block,
            )
        }

        fun isInterface() = iface { true }
        fun isClass() = cls { true }
        fun isObject() = obj { true }
        fun isClassOrObject() = classOrObject { true }
        fun isClassOrInterface() = classOrInterface { true }
        fun isProperty() = property { true }
        fun isFunction() = function { true }

        fun hasName(name: String) = any { declaration ->
            when (declaration) {
                is KoNameProvider -> declaration.name == name
                else -> false
            }
        }

        fun hasNameEndingWith(suffix: String) = any { declaration ->
            when (declaration) {
                is KoNameProvider -> declaration.name.endsWith(suffix)
                else -> false
            }
        }

        fun hasNameContaining(contains: String) = any {
            when (it) {
                is KoNameProvider -> it.name.contains(contains)
                else -> false
            }
        }

        fun hasParent(block: (KoParentDeclaration) -> Boolean) = any { declaration ->
            when (declaration) {
                is KoParentProvider -> declaration.parents().any { block(it) }
                else -> false
            }
        }

        fun hasParent(predicate: DefinitionPredicate<KoBaseDeclaration>) = any { declaration ->
            when (declaration) {
                is KoParentProvider -> declaration.parents().any { predicate.test(it) }
                else -> false
            }
        }

        fun hasNoParents() = any { declaration ->
            when (declaration) {
                is KoParentProvider -> declaration.parents().isEmpty()
                else -> false
            }
        }

        fun hasSingleParent() = any { declaration ->
            when (declaration) {
                is KoParentProvider -> declaration.parents().size == 1
                else -> false
            }
        }

        fun hasModifier(modifier: KoModifier) = any { declaration ->
            when (declaration) {
                is KoModifierProvider -> declaration.hasModifier(modifier)
                else -> false
            }
        }

        fun hasFileNameMatchingDeclarationName() = any { declaration ->
            require(declaration is KoNameProvider)
            val filePath = declaration.containingFilePath()
            val fileName = filePath.substringAfterLast("/").removeSuffix(".kt")
            return@any fileName == declaration.name
        }
    }
}

/**
 * Internal implementation of [DefinitionPredicate].
 *
 * Note: [test] uses [runCatching] to swallow exceptions thrown by [testPredicate]. This is
 * intentional — rule predicates commonly use [require] as type guards (e.g. `require(declaration
 * is KoFunctionDeclaration)`), and those guards throw [IllegalArgumentException] when the
 * declaration type doesn't match. Treating thrown exceptions as `false` (not matching) allows
 * these guard patterns to work naturally. The trade-off is that genuine bugs in rule logic will
 * also be silently swallowed rather than failing loudly.
 */
private class DefinitionPredicateImpl<Type : KoBaseDeclaration>(
    private val asType: (KoBaseDeclaration) -> Type?,
    private val testPredicate: (Type) -> Boolean,
): DefinitionPredicate<Type> {
    override fun test(declaration: KoBaseDeclaration?): Boolean {
        if (declaration is KoParentDeclaration) {
            return test(declaration.sourceDeclaration)
        }

        val isTopLevelProvider = declaration is KoIsTopLevelProvider
        val isFile = declaration is KoFileDeclaration
        if (!isTopLevelProvider && !isFile) return false
        val typedDeclaration = asType(declaration) ?: return false
        return runCatching { testPredicate(typedDeclaration) }
            .getOrElse { false }
    }
}
