package architecture.rules.clientui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

@Describe("""
    A small closed value type (enum, sealed class, or sealed interface) that lives in `..ui..`
    and crosses feature boundaries, such as a `Slot` tag that one feature's ViewModel passes back
    to another feature's screen.

    * **Note:** If a value type grows behaviour, it stops being a value type. Promote it into a
      State, Destination, or domain object as appropriate.
""")
object UiValueType : Construct<ClientUi>(
    requirements = listOf(
        oneOf(isEnum, isSealed),
        predicate("has no member functions") { declaration ->
            when (declaration) {
                is KoClassDeclaration -> declaration.functions().isEmpty()
                is KoInterfaceDeclaration -> declaration.functions().isEmpty()
                else -> false
            }
        },
    ),
)
