package architecture.rules.testing

import architecture.definitions.withoutStringsAndComments
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.provider.KoParentProvider
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A class extending `PageRobot` (`:platform:common:robots`): one page's user actions and what the
    user sees on it, `[Name]PageRobot`. Subcutaneous Tests and End To End Tests drive pages only
    through robots, and the same robot runs in both.

    ```kotlin
    fun User.onGreetingsPage(block: GreetingsPageRobot.() -> Unit) =
        on(setOf(UkptPaths.HOME, UkptPaths.GREETINGS), ::GreetingsPageRobot, block)

    class GreetingsPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
        private val form = Locator.css(UkptIds.greetingForm.selector)

        fun enterName(name: String) = enter(form.find("input[name=name]"), name)
        fun clickGreetButton() = click(form.find("button[type=submit]", hasText = "Greet"))
    }
    ```

    `on[Name]Page { }` waits until the tab shows one of the page's paths: a form posted without
    scripts re-renders the page at the post's path. A `Locator` is a CSS selector, narrowed by the
    text an element contains; robots build them from the feature's `[Name]Ids` so a robot and its
    page name an element with one string. A test reads as the steps a user takes, one block per
    page visit.
""")
object Robot : Construct<Testing>(
    requirements = listOf(
        isClass,
        predicate("extends `PageRobot`") { decl -> (decl as KoParentProvider).parents().any { it.name == "PageRobot" } },
    ),
) {
    @Describe("A Robot must live in a `:feature:[name]:tests` module's `src/main` source set")
    val placement by rule {
        rationale("The feature's Subcutaneous Tests and the application's End To End Tests both depend on its `:tests` module.")
        constrain(placement("a `:feature:[name]:tests` module's `src/main/`") { it.isInFeatureTestsModule() && it.sourceSet() == "main" })
    }

    @Describe("A Robot's file must declare exactly one entry point, `fun User.on[Name]Page(block: [Name]PageRobot.() -> Unit)`, named for the robot")
    val onePageEntryPoint by rule {
        rationale(
            """
            Tests reach a robot only through its entry point, which waits for the page before the
            first step. A step on another page belongs in that page's robot, in its own
            `on[Page] { }` block.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val robot = decl as KoClassDeclaration
            val entryPoints = ENTRY_POINT.findAll(decl.fileCode()).map { it.groupValues[1] }.toList()
            val expected = "on" + robot.name.removeSuffix("Robot")
            when {
                entryPoints.isEmpty() -> listOf(Violation(decl, "its file declares no `fun User.$expected(block: ${robot.name}.() -> Unit)`"))
                entryPoints.size > 1 -> listOf(Violation(decl, "its file declares ${entryPoints.size} entry points: ${entryPoints.joinToString()}"))
                entryPoints.single() != expected -> listOf(Violation(decl, "its entry point `${entryPoints.single()}` is not named `$expected`"))
                else -> emptyList()
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

    @Describe("A Robot's entry point must be called by a Subcutaneous Test")
    val usedBySubcutaneousTests by rule {
        rationale("The Subcutaneous Tests are a page's coverage. A robot that only End To End Tests use leaves the page's behaviour to the slowest suite.")
        scopes { scopes, exempt ->
            val subcutaneousCode = scopes.tests.classes()
                .filter { SubcutaneousTest.test(it) }
                .map { it.containingFile }
                .distinctBy { it.path }
                .map { it.text.withoutStringsAndComments() }
            scopes.tests.classes()
                .filter { Robot.test(it) }
                .filterNot { exempt(it) }
                .mapNotNull { robot ->
                    val entryPoint = "on" + robot.name.removeSuffix("Robot")
                    if (subcutaneousCode.any { Regex("""\b$entryPoint\s*[({]""").containsMatchIn(it) }) null
                    else Violation(robot, "no Subcutaneous Test calls `$entryPoint`")
                }
        }
    }
}

private val USER_ACTION = Regex("""(click|enter|clear|select|toggle|scroll|see)[A-Z]\w*""")
private val ENTRY_POINT = Regex("""\bfun\s+User\.(on[A-Z]\w*)\s*\(""")
