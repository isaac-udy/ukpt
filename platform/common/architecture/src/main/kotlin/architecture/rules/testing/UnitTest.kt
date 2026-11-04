package architecture.rules.testing

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@UnitTest` class: one production class exercised over many inputs, with every collaborator
    a fake declared in test sources or a lambda.

    A unit test is for logic whose input space is wider than the screens that reach it: parsers,
    validators, formatters, reducers, a UseCase with many branches. Behaviour that a user reaches
    through a screen is covered by a [Subcutaneous Test](#subcutaneous-test) instead, which does
    not change when the layers between the ViewModel and the database are restructured.
""")
object UnitTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("UnitTest")),
) {
    @Describe("A Unit Test must live in a test source set other than `androidHostTest`, outside a `:feature:[name]:tests` module")
    val placement by rule {
        rationale("`androidHostTest` runs Paparazzi; a `:tests` module holds Subcutaneous Tests.")
        constrain(
            placement("a `commonTest`, platform test, or `src/test` source set") { decl ->
                decl.sourceSet() != "androidHostTest" && !decl.isInFeatureTestsModule()
            },
        )
    }

    @Describe("A Unit Test must not import dependency injection, network, database, file-system, or UI-test APIs")
    val noInfrastructure by rule {
        rationale(
            """
            Those APIs are what the other categories use. A unit test that reaches them is a
            Wiring, Adapter, Subcutaneous or Component test, and is annotated as one.
            """.trimIndent(),
        )
        constrain(noImportsOf(INFRASTRUCTURE_IMPORTS))
    }

    @Describe("A Unit Test must construct at most one production class; constructing domain models, states and other value types is test data")
    val oneProductionClass by rule {
        rationale(
            """
            A test that assembles several production classes is testing their combination, which
            is what a Subcutaneous Test covers with the real dependency graph.
            """.trimIndent(),
        )
        note("Constructed means the class name followed by `(` in the test file's code.")
        constrainWithScopes { decl, scopes, _ ->
            val code = decl.fileCode()
            val constructed = scopes.productionReferences(decl.file)
                .filter { it.declaration is KoClassDeclaration }
                .filterNot { reference -> reference.constructs.any { it.id in VALUE_CONSTRUCTS } }
                .filter { Regex("""\b${it.name}\s*\(""").containsMatchIn(code) }
                .map { it.name }
                .distinct()
            if (constructed.size <= 1) {
                emptyList()
            } else {
                listOf(Violation(decl, "constructs ${constructed.size} production classes: ${constructed.joinToString()}"))
            }
        }
    }
}

internal val INFRASTRUCTURE_IMPORTS = listOf(
    "org.koin.",
    "io.ktor.",
    "org.jetbrains.exposed.",
    "java.sql.",
    "java.io.File",
    "java.nio.file.",
    "kotlinx.io.files.",
    "dev.isaacudy.udytils.postgres.",
    "androidx.compose.ui.test.",
    "org.jetbrains.compose.web.testutils.",
    "app.cash.paparazzi.",
)
