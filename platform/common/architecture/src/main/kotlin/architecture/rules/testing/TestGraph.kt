package architecture.rules.testing

import architecture.definitions.containingFilePath
import dev.isaacudy.udytils.architecture.*

internal val isSubcutaneousGraph = isPropertyWhere("is a top-level property initialised with `SubcutaneousGraph(`") { property ->
    property.isTopLevel && Regex("""=\s*SubcutaneousGraph\s*\(""").containsMatchIn(property.text)
}

@Describe("""
    A top-level `SubcutaneousGraph` in a `:feature:[name]:tests` module's `src/main`: the production
    Koin modules the feature's Subcutaneous Tests run, `[name]TestGraph`.

    ```kotlin
    val ukptTestGraph = SubcutaneousGraph(
        client = featureClient(ukptClientDependencies),
        server = featureServer(ukptServerDependencies),
    )
    ```

    `featureClient` adds the network modules and `featureServer` the Postgres modules. A test
    replaces a binding at the system's edges (a backend the project does not own, a clock) with
    `subcutaneous(graph, edges = { server { … } })`, not in the graph.

    A project with no server of its own lists only client modules, including the `:platform`
    module that builds its HTTP client, and fakes the backend at the client edge:

    ```kotlin
    val projectsTestGraph = SubcutaneousGraph(
        client = listOf(networkDependencies, projectsClientDependencies),
    )
    ```
""")
object TestGraph : Construct<Testing>(
    requirements = listOf(isSubcutaneousGraph),
) {
    @Describe("A `:feature:[name]:tests` module must declare exactly one Test Graph, and a Test Graph must live in one")
    val onePerTestsModule by rule {
        rationale("The graph repeats the module list the `:app` shells install for the feature. A second graph is a second list to keep in step with them.")
        scope { scope, exempt ->
            val graphs = scope.properties().filter { TestGraph.test(it) }.filterNot { exempt(it) }
            val modules = scope.files.mapNotNull { file -> TESTS_MODULE.find(file.path)?.let { file.path.substringBefore("/src/") } }.toSet()
            val outside = graphs.filterNot { it.isInFeatureTestsModule() }.map { Violation(it, "lives outside a `:feature:[name]:tests` module") }
            val counted = modules.sorted().mapNotNull { module ->
                val inModule = graphs.filter { it.containingFilePath().startsWith("$module/src/") }
                when (inModule.size) {
                    1 -> null
                    0 -> Violation(module, "declares no Test Graph")
                    else -> Violation(module, "declares ${inModule.size} Test Graphs: ${inModule.joinToString { it.name }}")
                }
            }
            outside + counted
        }
    }

    @Describe("A Test Graph must list production Koin modules only: each feature's `[name]ClientDependencies` and `[name]ServerDependencies`, and `:platform` modules")
    val productionModulesOnly by rule {
        rationale("A binding declared in the graph replaces production code for every test. Replacements at the system's edges go in `subcutaneous(graph, edges = { … })`.")
        constrainWithScopes { decl, scopes, _ ->
            val inline = if (Regex("""\bmodule\s*\{""").containsMatchIn(decl.fileCode())) listOf(Violation(decl, "declares a Koin module inline")) else emptyList()
            inline + scopes.referencesOutside(decl, setOf("FeatureRules.DependencyModule"))
                .map { Violation(decl, "references `${it.name}` (${it.constructs.joinToString { c -> c.id }})") }
        }
    }
}

private val TESTS_MODULE = Regex("""/feature/(?:[^/]+/)+tests/src/""")
