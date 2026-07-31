package architecture.definitions

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * The dotted or simple type tokens a type expression mentions — `Lazy<vendor.time.Clock>` yields
 * `["Lazy", "vendor.time.Clock"]` — kept whole, so a qualified reference stays resolvable instead
 * of collapsing to a simple name that could collide with an unrelated declaration's.
 */
fun typeTokens(typeExpression: String): List<String> =
    Regex("""[A-Za-z_][A-Za-z0-9_.]*""").findAll(typeExpression).map { it.value }.toList()

/**
 * Resolves a type token the way the file's reader would: an import alias wins, then a plain import
 * whose last segment matches, then the file's own package; a token written qualified resolves as
 * itself. This is what makes reference matching both alias-proof (`import Foo as Bar` resolves
 * `Bar` to `Foo`'s FQN) and collision-proof (a vendor type sharing a project type's simple name
 * resolves to the vendor's FQN, not the project's).
 *
 * Wildcard imports are not resolved through — `ProjectRules.noWildcardImports` bans them, so a
 * simple token always has a single-name import or is a same-package reference.
 */
fun KoFileDeclaration.resolveTypeToken(token: String): String? {
    if ('.' in token) return token
    imports.firstOrNull { it.alias?.name == token }?.let { return it.name }
    imports.firstOrNull { it.alias == null && it.name.substringAfterLast('.') == token }?.let { return it.name }
    return packagee?.name?.let { "$it.$token" }
}

/** True when any token of [typeExpression] resolves, through this file's imports, to an FQN in [candidates]. */
fun KoFileDeclaration.typeExpressionResolvesTo(typeExpression: String, candidates: Set<String>): Boolean =
    typeTokens(typeExpression).any { token -> resolveTypeToken(token) in candidates }
