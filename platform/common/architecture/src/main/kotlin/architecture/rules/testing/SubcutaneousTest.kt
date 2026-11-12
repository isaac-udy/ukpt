package architecture.rules.testing

import architecture.definitions.withoutStringsAndComments
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@SubcutaneousTest` class: drives a real entry point (a ViewModel, or a `@Urpc` service for
    server behaviour that no screen uses) built from the production DI graph, with the real
    server running in-process against an embedded database. It asserts on what the entry point
    exposes: ViewModel state, navigation results, service responses.

    Subcutaneous tests are the main suite. Subcutaneous tests exercise the code a user runs through
    a screen, and do not change when the domain and data layers below the ViewModel are restructured.

    ```kotlin
    @SubcutaneousTest
    class UkptViewModelTest {
        @Test
        fun `a greeting added while the screen is open appears`() = subcutaneous(ukptTestGraph) {
            val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
            screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }

            given { greetingHistory("Kia ora") }

            screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Kia ora") }
        }
    }
    ```

    `subcutaneous(graph)` gives each test a new database cloned from the migrated template, runs
    the [Test Graph](#test-graph)'s server modules in-process on Ktor's test engine, and builds
    the client graph against it. `viewModel<VM, K>(key)` opens a ViewModel from the client graph
    with an enro-test navigation handle; `service<S>()` calls a `@Urpc` service through the client
    transport. `given { }` runs [Scenarios](#scenario), before or after a ViewModel opens.
    `awaitMatching` waits up to 10 seconds of real time for the state to match.
""")
object SubcutaneousTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("SubcutaneousTest")),
) {
    @Describe("A Subcutaneous Test must live in the `src/test` source set of a `:feature:[name]:tests` module")
    val placement by rule {
        rationale(
            """
            The `:tests` module depends on the feature's `:client` and `:server`, which no other
            module may combine.
            """.trimIndent(),
        )
        constrain(
            placement("`:feature:[name]:tests`, `src/test/`") { decl ->
                decl.isInFeatureTestsModule() && decl.sourceSet() == "test"
            },
        )
    }

    @Describe("A Subcutaneous Test must reference only entry-point vocabulary from production code: ViewModels, their states, Destinations, UI value types, client domain models and their extensions, domain exceptions and constants, shared wire types, and `@Urpc` service interfaces")
    val entryPointVocabularyOnly by rule {
        rationale(
            """
            The test drives the entry point and reads what it exposes. A reference to a repository,
            a use case or a storage class fixes that class in place, and the test changes when the
            layers below the entry point are restructured.
            """.trimIndent(),
        )
        note("Test data comes from Scenarios (`given { }`), which may use the server domain.")
        constrainWithScopes { decl, scopes, _ ->
            scopes.referencesOutside(decl, ENTRY_POINT_CONSTRUCTS)
                .map { Violation(decl, "references `${it.name}` (${it.constructs.joinToString { c -> c.id }})") }
        }
    }

    @Describe("A Subcutaneous Test must not construct a ViewModel; it opens one from the client graph with `viewModel<[Name]ViewModel, [Name]Destination>(key)`")
    val viewModelsFromTheGraph by rule {
        rationale("A ViewModel built by hand takes collaborators the test chose, not the ones the application binds.")
        constrainWithScopes { decl, scopes, _ ->
            val code = decl.fileCode()
            scopes.productionReferences(decl.file)
                .filter { reference -> reference.constructs.any { it.id == "ClientUi.ViewModel" } }
                .filter { Regex("""\b${it.name}\s*\(""").containsMatchIn(code) }
                .map { Violation(decl, "constructs `${it.name}`") }
        }
    }

    @Describe("Every `@Test` function of a Subcutaneous Test must run its body in `subcutaneous(graph) { … }`")
    val runsInTheHarness by rule {
        rationale("The harness starts the server and the client graph against a new database, and tears them down after the test.")
        constrain { decl, _ ->
            (decl as KoClassDeclaration).functions()
                .filter { it.hasAnnotationWithName("Test") }
                .filterNot { Regex("""\bsubcutaneous\s*\(""").containsMatchIn(it.text.withoutStringsAndComments()) }
                .map { Violation(it, "`${it.name}` does not call `subcutaneous(`") }
        }
    }

    @Describe("A Subcutaneous Test must not import Koin, Ktor, Exposed, JDBC or `kotlinx.coroutines.test`")
    val noInfrastructureImports by rule {
        rationale(
            """
            The harness owns the graph, the server, the database and the dispatchers. Virtual time
            from `kotlinx.coroutines.test` does not advance real I/O; `awaitMatching` waits in real
            time.
            """.trimIndent(),
        )
        constrain(noImportsOf(listOf("org.koin.", "io.ktor.", "org.jetbrains.exposed.", "java.sql.", "javax.sql.", "kotlinx.coroutines.test.")))
    }
}

private val ENTRY_POINT_CONSTRUCTS = setOf(
    "ClientUi.ViewModel",
    "ClientUi.ViewModelState",
    "ClientUi.Destination",
    "ClientUi.UiValueType",
    "ClientDomain.DomainModel",
    "ClientDomain.ExtensionFunction",
    "ClientDomain.ExtensionProperty",
    "ClientDomain.DomainException",
    "ClientDomain.Constants",
    "FeatureRules.SharedDomainModel",
    "FeatureRules.SharedException",
    "FeatureRules.SharedConstants",
    "FeatureRules.SharedExtensionFunction",
    "FeatureRules.SharedExtensionProperty",
    "ServerServices.ServiceInterface",
)
