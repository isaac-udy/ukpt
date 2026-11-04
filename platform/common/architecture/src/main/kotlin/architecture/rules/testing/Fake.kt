package architecture.rules.testing

import architecture.definitions.containingFilePackage
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoParentProvider
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A class or object in test sources named `Fake[Name]`: a hand-written stand-in for a production
    interface. A `fun interface` collaborator is faked with a lambda instead, which needs no
    declaration.
""")
object Fake : Construct<Testing>(
    requirements = listOf(
        oneOf(isClass, isObject),
        predicate("is named `Fake[Name]`") { (it as? KoNameProvider)?.name?.startsWith("Fake") == true },
        predicate("declares no `@Test` function") { decl ->
            val functions = when (decl) {
                is KoClassDeclaration -> decl.functions()
                is KoObjectDeclaration -> decl.functions()
                else -> emptyList()
            }
            functions.none { it.hasAnnotationWithName("Test") }
        },
    ),
) {
    @Describe("In a `:feature:[name]:tests` module, a Fake must implement only an interface an IntegrationClient provides, `kotlin.time.Clock`, or an interface declared under `platform`")
    val subcutaneousEdgesOnly by rule {
        rationale(
            """
            A Subcutaneous Test runs the production graph. A fake replaces a binding only at the
            system's edges: a backend the project does not own, the clock, a platform service. It
            is bound in `subcutaneous(graph, edges = { server { … } })`.
            """.trimIndent(),
        )
        constrainWithScopes { decl, scopes, _ ->
            if (!decl.isInFeatureTestsModule()) return@constrainWithScopes emptyList()
            val integrationInterfaces = scopes.production.classes()
                .filter { cls -> scopes.constructsOf(cls).any { it.id == "ServerData.IntegrationClient" } }
                .flatMap { client ->
                    client.parents().map { it.name } +
                        client.properties().mapNotNull { it.type?.name } +
                        client.properties().mapNotNull { PROVIDED.find(it.text)?.groupValues?.get(1) }
                }
                .toSet()
            val references = scopes.productionReferences(decl.file)
            val importsClock = decl.file.imports.any { it.name == "kotlin.time.Clock" }
            (decl as KoParentProvider).parents()
                .map { it.name.substringBefore('<') }
                .filterNot { parent ->
                    (parent == "Clock" && importsClock) ||
                        parent in integrationInterfaces ||
                        references.any { it.name == parent && it.declaration.containingFilePackage().startsWith("platform.") }
                }
                .map { Violation(decl, "implements `$it`, which no IntegrationClient provides") }
        }
    }
}

private val PROVIDED = Regex("""=\s*(\w+)\s*\{""")
