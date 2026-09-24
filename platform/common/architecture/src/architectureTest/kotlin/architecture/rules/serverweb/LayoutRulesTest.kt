package architecture.rules.serverweb

import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Layout rules over `src/architectureTest/fixtures/web-layouts`: `shopShellLayout` takes its
 * View State and renders through the platform's document, and `ordersPage` renders through it —
 * both must stay quiet — while `bareLayout` takes plain values and renders its own head and body.
 */
class LayoutRulesTest {

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(File("src/architectureTest/fixtures/web-layouts").absolutePath) },
        membership = UkptArchitecture.membership,
    )

    private fun messages(ruleId: String): List<String> =
        run.violations(run.rules.first { it.id == ruleId }).map { it.message }

    @Test
    fun `a layout that takes plain values is reported`() {
        assertEquals(listOf("bareLayout"), messages("ServerWeb.Layout.takesItsViewStateAndContent").map { it.substringAfter('`').substringBefore('`') })
    }

    @Test
    fun `a layout that renders its own document is reported`() {
        assertEquals(listOf("bareLayout"), messages("ServerWeb.Layout.rendersThroughThePlatform").map { it.substringAfter('`').substringBefore('`') })
        assertEquals(listOf("<body>", "<head>"), messages("ServerWeb.documentFromThePlatform").map { it.substringAfter('`').substringBefore('`') }.sorted())
    }

    @Test
    fun `a page may render through a feature's layout`() {
        assertEquals(emptyList(), messages("ServerWeb.Page.rendersThroughLayout"))
        assertEquals(emptyList(), messages("ServerWeb.Page.takesItsViewState"))
        assertEquals(emptyList(), messages("ServerWeb.exhaustive"))
    }
}
