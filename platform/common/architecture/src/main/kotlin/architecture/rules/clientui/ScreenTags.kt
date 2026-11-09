package architecture.rules.clientui

import com.lemonappdev.konsist.api.provider.KoNameProvider
import dev.isaacudy.udytils.architecture.*

@Describe("""
    An `object` named `[Screen]Tags` whose members are `const val` test tags: the names a screen
    gives its nodes with `Modifier.testTag`, and the names its [Robot](testing.md#robot) finds them
    by. Each screen a robot drives has one, in the screen's package.

    ```kotlin
    object UkptScreenTags {
        const val Screen = "UkptScreen"
        const val GreetButton = "UkptScreen.GreetButton"
    }
    ```
""")
object ScreenTags : Construct<ClientUi>(
    requirements = listOf(
        predicate("is named `[Screen]Tags`") { (it as? KoNameProvider)?.name?.endsWith("Tags") == true },
        isObjectWhere("is an `object` with only `const val` properties and no functions") { decl ->
            decl.functions().isEmpty() && decl.properties().all { it.hasConstModifier }
        },
    ),
)
