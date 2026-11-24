package architecture.rules.testing

import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import dev.isaacudy.udytils.architecture.*

internal val isHostEdges = isPropertyWhere("is a top-level property of type `Edges.() -> Unit`") { property ->
    property.isTopLevel && property.type?.text?.replace(" ", "") == "Edges.()->Unit"
}

@Describe("""
    A top-level `Edges.() -> Unit` in a `:feature:[name]:tests` module, `[name]HostEdges`: bindings
    that replace production ones a test host cannot provide, such as a database driver or a
    DataStore that needs an Android `Context`, for every test the [Test Graph](#test-graph) runs
    on that host. The graph declares it with `expect` in `src/commonMain`; each host's source set
    declares the `actual`.

    ```kotlin
    // src/commonMain
    expect val notesHostEdges: Edges.() -> Unit

    val notesTestGraph = SubcutaneousGraph(
        client = featureClient(notesClientDependencies),
        server = featureServer(notesServerDependencies),
        hostEdges = notesHostEdges,
    )

    // src/androidMain
    actual val notesHostEdges: Edges.() -> Unit = {
        client { single<SqlDriver> { JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) } }
    }

    // src/jvmMain
    actual val notesHostEdges: Edges.() -> Unit = {}
    ```

    The harness loads host edges after the graph and before a test's own `edges`, so a test can
    still replace a host binding.
""")
object HostEdges : Construct<Testing>(
    testedBy = TestExpectation.none("test infrastructure"),
    requirements = listOf(isHostEdges),
) {
    @Describe("A Host Edges declaration must live in a `:feature:[name]:tests` module's `src/commonMain` or a host's main source set")
    val placement by rule {
        rationale("The Test Graph in the same module passes the declaration to `SubcutaneousGraph(hostEdges = …)`; a test's own replacements go in `subcutaneous(graph, edges = { … })`.")
        constrain(
            placement("a `:feature:[name]:tests` module's `src/commonMain` or a host's main source set") { decl ->
                decl.isInFeatureTestsModule() && decl.sourceSet().endsWith("Main")
            },
        )
    }

    @Describe("A Host Edges declaration must be named `[name]HostEdges`")
    val naming by rule {
        rationale("The name states that the bindings apply to every test on a host, not to one test.")
        constrain { decl, _ ->
            val name = (decl as KoPropertyDeclaration).name
            if (name.endsWith("HostEdges")) emptyList() else listOf(Violation(decl, "`$name` is not named `[name]HostEdges`"))
        }
    }
}
