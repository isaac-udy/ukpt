package architecture.rules.testing

import architecture.definitions.containingFilePath
import architecture.definitions.withoutStringsAndComments
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import dev.isaacudy.udytils.architecture.ArchitectureScopes
import dev.isaacudy.udytils.architecture.ConstructCheck
import dev.isaacudy.udytils.architecture.Violation

/** The source set a test declaration lives in: `commonTest`, `jsTest`, `test`, … */
internal fun KoBaseDeclaration.sourceSet(): String =
    SOURCE_SET.findAll(containingFilePath()).lastOrNull()?.groupValues?.get(1).orEmpty()

/** The module directory a declaration lives in: everything before its source set's `/src/`. */
internal fun KoBaseDeclaration.moduleDirectory(): String = containingFilePath().substringBeforeLast("/src/")

internal fun KoBaseDeclaration.isInFeatureTestsModule(): Boolean = FEATURE_TESTS_MODULE.containsMatchIn(containingFilePath())

internal val KoBaseDeclaration.file: KoFileDeclaration
    get() = (this as KoContainingFileProvider).containingFile

/** The declaration's file, without comments and string literals. */
internal fun KoBaseDeclaration.fileCode(): String = file.text.withoutStringsAndComments()

private val SOURCE_SET = Regex("/src/([^/]+)/")
private val FEATURE_TESTS_MODULE = Regex("/feature/[^/]+/tests/src/")

/** A placement check: the declaration's module and source set must satisfy [allowed]. */
internal fun placement(where: String, allowed: (KoBaseDeclaration) -> Boolean) = ConstructCheck { decl, _ ->
    if (allowed(decl)) emptyList() else listOf(Violation(decl, "lives in `${decl.moduleDirectory()}/src/${decl.sourceSet()}/`; belongs in $where"))
}

/** A check that the declaration's file imports nothing under [prefixes]. */
internal fun noImportsOf(prefixes: List<String>) = ConstructCheck { decl, _ ->
    decl.file.imports
        .filter { import -> prefixes.any { import.name.startsWith(it) } }
        .map { Violation(decl, "imports `${it.name}`") }
}

/** Value Constructs: constructing one in a test is test data, not exercising a production class. */
internal val VALUE_CONSTRUCTS = setOf(
    "FeatureRules.SharedDomainModel",
    "FeatureRules.SharedException",
    "FeatureRules.SharedConstants",
    "ServerDomain.DomainModel",
    "ServerDomain.DomainException",
    "ServerDomain.Constants",
    "ServerWeb.ViewState",
    "ServerWeb.Form",
    "ServerData.Configuration",
    "ServerData.StorageRecord",
)

/** A `:feature:[name]:server` module or `:platform:server:web`: the modules that hold markup. */
internal fun KoBaseDeclaration.isInServerWebModule(): Boolean {
    val module = moduleDirectory()
    return module.endsWith("/platform/server/web") || FEATURE_SERVER_MODULE.containsMatchIn(module)
}

private val FEATURE_SERVER_MODULE = Regex("/feature/[^/]+/server$")

/** Production references of the declaration's file that match a Construct outside [allowedConstructs]. */
internal fun ArchitectureScopes.referencesOutside(decl: KoBaseDeclaration, allowedConstructs: Set<String>) =
    productionReferences(decl.file)
        .filter { reference -> reference.constructs.isNotEmpty() && reference.constructs.none { it.id in allowedConstructs } }
