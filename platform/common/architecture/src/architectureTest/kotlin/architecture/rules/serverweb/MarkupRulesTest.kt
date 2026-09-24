package architecture.rules.serverweb

import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `server.web` import and markup rules over `src/architectureTest/fixtures/web-markup`: a
 * `feature.shop` server whose `CheckoutMarkup.kt` imports a Repository and Koin, writes two
 * attribute names as strings, sets an event handler both ways, loads a script and a stylesheet
 * from other origins, and writes an inline script — beside a same-origin script with a `defer`
 * body and a same-origin stylesheet, which must stay quiet.
 */
class MarkupRulesTest {

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(File("src/architectureTest/fixtures/web-markup").absolutePath) },
        membership = UkptArchitecture.membership,
    )

    private fun messages(ruleId: String): List<String> =
        run.violations(run.rules.first { it.id == ruleId }).map { it.message }

    @Test
    fun `persistence and Koin imports are reported`() {
        assertEquals(1, messages("ServerWeb.noDataImports").size)
        assertEquals(1, messages("ServerWeb.noKoinImports").size)
    }

    @Test
    fun `string attribute names are reported`() {
        assertEquals(
            listOf("hx-post", "x-on:click"),
            messages("ServerWeb.typedHypermediaAttributes").map { it.substringAfter('`').substringBefore('`') },
        )
    }

    @Test
    fun `inline script and unescaped HTML are reported, a deferred script is not`() {
        assertEquals(
            listOf("attributes[\"onclick\"]", "onClick =", "script { … }", "unsafe { … }"),
            messages("ServerWeb.noInlineScript").map { it.substringAfter('`').substringBeforeLast('`') }.sorted(),
        )
    }

    @Test
    fun `scripts and stylesheets from other origins are reported`() {
        assertEquals(
            listOf("//cdn.example.com/checkout.css", "https://unpkg.com/htmx.org@2.0.0"),
            messages("ServerWeb.selfHostedAssets").map { it.substringAfter('`').substringBefore('`') }.sorted(),
        )
    }
}
