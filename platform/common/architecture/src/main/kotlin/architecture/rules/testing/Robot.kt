package architecture.rules.testing

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.provider.KoParentProvider
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A class extending `ScreenRobot` (`:platform:common:robots`): one screen's user actions and what
    the user sees on it. Component Tests and End To End Tests drive screens only through robots.

    ```kotlin
    fun User.onUkptScreen(block: UkptScreenRobot.() -> Unit) =
        on(UkptDestination::class, UkptScreenTags.Screen, ::UkptScreenRobot, block)

    class UkptScreenRobot internal constructor(context: RobotContext) : ScreenRobot(context) {
        fun clickGreetButton() = click(UkptScreenTags.GreetButton)
        fun seeGreeting(text: String) = seeText(UkptScreenTags.LatestGreeting, text)
    }
    ```

    A test reads as the steps a user takes, one block per screen:

    ```kotlin
    asUser(rootContext) {
        onUkptScreen {
            clickResetButton()
        }
        onConfirmResetDialog {
            clickResetButton()
        }
        onUkptScreen {
            seeNoGreetings()
        }
    }
    ```

    `on[Screen] { }` waits until the screen's tag is shown. Given the application's `RootContext`,
    as in an End To End Test, it also waits until the screen's destination is the active one, so a
    step can't act on a screen behind a dialog.
""")
object Robot : Construct<Testing>(
    requirements = listOf(
        isClass,
        predicate("extends `ScreenRobot`") { decl -> (decl as KoParentProvider).parents().any { it.name == "ScreenRobot" } },
    ),
) {
    @Describe("A Robot must live in a `:feature:[name]:tests` module's `src/main` source set")
    val placement by rule {
        rationale("The feature's Component Tests and the application's End To End Tests both depend on its `:tests` module.")
        constrain(placement("a `:feature:[name]:tests` module's `src/main/`") { it.isInFeatureTestsModule() && it.sourceSet() == "main" })
    }

    @Describe("A Robot must reference exactly one Screen Tags object")
    val oneScreen by rule {
        rationale("A robot drives one screen. A step on another screen belongs in that screen's robot, in its own `on[Screen] { }` block.")
        constrainWithScopes { decl, scopes, _ ->
            val tags = scopes.productionReferences(decl.file)
                .filter { reference -> reference.constructs.any { it.id == "ClientUi.ScreenTags" } }
                .map { it.name }
                .distinct()
            when (tags.size) {
                1 -> emptyList()
                0 -> listOf(Violation(decl, "references no Screen Tags object"))
                else -> listOf(Violation(decl, "references ${tags.joinToString { "`$it`" }}"))
            }
        }
    }

    @Describe("Every public function of a Robot must be named for a user action (`click…`, `enter…`, `clear…`, `select…`, `toggle…`, `scroll…`) or for what the user sees (`see…`)")
    val userActionNames by rule {
        rationale(
            """
            A test reads as what a user would report: "I click Greet, then I see Hello again". A
            name such as `greet()` hides which control the step uses.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            (decl as KoClassDeclaration).functions()
                .filterNot { it.hasPrivateModifier || it.hasProtectedModifier || it.hasInternalModifier }
                .filterNot { USER_ACTION.matches(it.name) }
                .map { Violation(it, "`${it.name}` is not named `click…`, `enter…`, `clear…`, `select…`, `toggle…`, `scroll…` or `see…`") }
        }
    }

    @Describe("A Robot's file must declare its entry point, a `User.on[Screen]` function")
    val hasEntryPoint by rule {
        rationale("Tests reach a robot only through `on[Screen] { }`, which waits for the screen before the first step.")
        constrain { decl, _ ->
            if (ENTRY_POINT.containsMatchIn(decl.fileCode())) emptyList()
            else listOf(Violation(decl, "its file declares no `fun User.on[Screen](block: ${(decl as KoClassDeclaration).name}.() -> Unit)`"))
        }
    }
}

private val USER_ACTION = Regex("""(click|enter|clear|select|toggle|scroll|see)[A-Z]\w*""")
private val ENTRY_POINT = Regex("""\bfun\s+User\.on[A-Z]\w*\s*\(""")

/** Calls that find or act on semantics nodes directly. */
private val NODE_ACCESS = Regex("""\b(onNode\w*|onAllNodes\w*|onRoot|perform\w+|waitUntil(ExactlyOneExists|AtLeastOneExists|DoesNotExist|NodeCount))\s*\(""")

/** A check that the test's file finds and acts on nodes only through robots. */
internal val nodesOnlyThroughRobots = ConstructCheck { decl, _ ->
    val code = decl.fileCode()
    NODE_ACCESS.findAll(code).map { it.groupValues[1] }.distinct().toList()
        .map { Violation(decl, "calls `$it`; drive the screen through its Robot") }
}
