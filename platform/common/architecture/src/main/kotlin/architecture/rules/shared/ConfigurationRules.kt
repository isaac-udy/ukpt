package architecture.rules.shared

import architecture.definitions.containingFilePackage
import architecture.definitions.isAppModule
import architecture.definitions.isMutable
import architecture.definitions.resolveTypeToken
import architecture.utils.koinModuleFiles
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import dev.isaacudy.udytils.architecture.*

/**
 * The configuration rules, declared once and instantiated by each side's data group as
 * `object Configuration : ConfigurationRules<Group>(side)`. [side] scopes the residence
 * requirement to that side's `data` layer.
 */
abstract class ConfigurationRules<G : RuleGroup>(
    private val side: String,
) : Construct<G>(
    requirements = listOf(
        isClassWhere("is a `data class`") { it.hasDataModifier },
        isClassWhere("is named `[Name]Config` or `[Name]Configuration`") { it.name.endsWith("Config") || it.name.endsWith("Configuration") },
        predicate("resides in `feature.[name].$side.data..`") { it.containingFilePackage().contains(".$side.data") },
    ),
) {
    @Describe("A Configuration must be immutable — no `var` properties")
    val immutable by rule {
        rationale("A configuration is shared by every class the graph injects it into; a `var` lets one consumer change another's settings after the graph is assembled.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties().filter { it.isMutable() }.map { Violation(it, "configuration has a mutable (`var`) property") }
        }
    }

    @Describe("A Configuration is constructed in a dependency module or an `:app` module, never by the class that consumes it")
    val assembledAtTheCompositionBoundary by rule {
        rationale("The consuming class receives its configuration through its constructor, so the values are decided where the graph is assembled and a test supplies other values the same way. A class that constructs its own configuration holds settings nothing outside it decides.")
        note("A file that declares Koin bindings, or any file of an `:app` module, may construct it; the configuration's own file may declare a companion value. Test sources are outside the scope.")
        scope { scope, exempt ->
            val boundaryPaths = scope.koinModuleFiles().map { it.path }.toSet()
            scope.classes()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .flatMap { config ->
                    val fqn = (config as? KoFullyQualifiedNameProvider)?.fullyQualifiedName ?: return@flatMap emptyList()
                    val call = Regex("""(?<![.\w:])${Regex.escape(config.name)}\s*\(""")
                    scope.files
                        .filter { it.path !in boundaryPaths && !it.isAppModule() && it.path != config.containingFile.path }
                        .filter { file -> call.containsMatchIn(file.text) && file.resolveTypeToken(config.name) == fqn }
                        .map { Violation(it.path, "`${config.name}` is constructed outside a dependency module — assemble it where the graph is, and inject it") }
                }
        }
    }
}
