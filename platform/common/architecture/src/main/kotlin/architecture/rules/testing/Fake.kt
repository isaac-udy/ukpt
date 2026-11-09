package architecture.rules.testing

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider
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
)
