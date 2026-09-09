package architecture.utils

import architecture.definitions.resolveTypeToken
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider

/** Koin's constructor-reference DSL (`singleOf`, `factoryOf`, `scopedOf`, `viewModelOf`) has overloads up to this many parameters. */
const val KOIN_MAX_CONSTRUCTOR_REF_PARAMS = 22

/** A project class a Koin module registers: by constructor reference, or by constructing it inside a binding lambda. */
data class KoinRegistration(
    val cls: KoClassDeclaration,
    val file: KoFileDeclaration,
    val byReference: Boolean,
)

/** Every file that declares Koin bindings, on any module of the project: feature roots, platform modules, and the `:app` shells. */
fun KoScope.koinModuleFiles(): List<KoFileDeclaration> =
    files.filter { file ->
        file.imports.any { it.name == "org.koin.dsl.module" || it.name.startsWith("org.koin.core.module.") }
    }

fun KoScope.projectClassesByFqn(): Map<String, KoClassDeclaration> =
    classes()
        .mapNotNull { cls -> (cls as? KoFullyQualifiedNameProvider)?.fullyQualifiedName?.let { it to cls } }
        .toMap()

/** A value assembled by hand rather than a service the graph assembles. */
fun KoClassDeclaration.isValueShape(): Boolean =
    hasDataModifier || hasValueModifier || hasEnumModifier || hasSealedModifier || hasAbstractModifier

private val referenceRegistration = Regex("""\b(?:singleOf|factoryOf|scopedOf|viewModelOf)\s*\(\s*::([A-Z][A-Za-z0-9_]*)""")
private val bindingLambdaHead = Regex("""\b(?:single|factory|scoped|viewModel)\b\s*(?:<[^{}]*?>)?\s*(?:\([^{}]*?\))?\s*\{""")
private val runtimeParameters = Regex("""^\s*(?:\([^)]*\)|[A-Za-z_][A-Za-z0-9_]*)\s*->""")
private val constructorCall = Regex("""(?<![.\w:])([A-Z][A-Za-z0-9_]*)\s*\(""")

/**
 * The project classes this Koin module file registers. A constructor reference is a registration
 * by name; a capitalised call inside a `single`/`factory`/`scoped`/`viewModel` lambda is one when
 * it resolves through the file's imports to a project class that is not a value shape. A lambda
 * declaring runtime parameters (`factory { params -> … }`) is the form Koin gives that case and
 * is not read.
 */
fun KoFileDeclaration.koinRegistrations(classesByFqn: Map<String, KoClassDeclaration>): List<KoinRegistration> {
    val byReference = referenceRegistration.findAll(text)
        .mapNotNull { match -> resolveTypeToken(match.groupValues[1])?.let { classesByFqn[it] } }
        .map { KoinRegistration(it, this, byReference = true) }
    val constructed = bindingLambdaBodies(text)
        .filterNot { runtimeParameters.containsMatchIn(it) }
        .flatMap { body -> constructorCall.findAll(body).map { it.groupValues[1] } }
        .mapNotNull { name -> resolveTypeToken(name)?.let { classesByFqn[it] } }
        .filterNot { it.isValueShape() }
        .map { KoinRegistration(it, this, byReference = false) }
    return (byReference + constructed).distinctBy { it.cls.name to it.byReference }.toList()
}

/** The brace-balanced bodies of the binding lambdas in a Koin module file. */
internal fun bindingLambdaBodies(text: String): List<String> =
    bindingLambdaHead.findAll(text).map { head ->
        val start = head.range.last + 1
        var depth = 1
        var index = start
        while (index < text.length && depth > 0) {
            when (text[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            index++
        }
        text.substring(start, (index - 1).coerceAtLeast(start))
    }.toList()
