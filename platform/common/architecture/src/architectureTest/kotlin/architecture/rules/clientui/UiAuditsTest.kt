package architecture.rules.clientui

import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.Violation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The client-UI audits over `src/architectureTest/fixtures/ui-audits`: a `feature.shop` client whose
 * `SaveState` stores a `saving` flag beside a computed `deleting`, whose `RefundState` pairs a
 * stored `loading` with an `error` beside a computed `refreshing`, and whose `ShopScreen.kt` states
 * its preview viewport as `360.dp` while `ShopBadge.kt` hard-codes a colour and a padding.
 */
class UiAuditsTest {

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(File("src/architectureTest/fixtures/ui-audits").absolutePath) },
        membership = UkptArchitecture.membership,
    )

    private fun violations(ruleId: String): List<Violation> =
        run.violations(run.rules.first { it.id == ruleId })

    private fun findings(ruleId: String): List<Violation> =
        run.auditFindings(run.rules.first { it.id == ruleId })

    @Test
    fun `a stored progress flag is reported and a computed one derived from an AsyncState is not`() {
        val messages = findings("ClientUi.ViewModelState.usesAsyncState").map { it.message }
        assertEquals(1, messages.size, messages.toString())
        assertTrue("`saving: Boolean`" in messages.single(), messages.single())
    }

    @Test
    fun `a stored progress flag beside an error is reported and a computed one is not`() {
        val messages = violations("ClientUi.ViewModelState.noManualAsyncLifecycleFields").map { it.message }
        assertEquals(1, messages.size, messages.toString())
        assertTrue("`loading: Boolean` paired with `error`" in messages.single(), messages.single())
    }

    @Test
    fun `a literal in a preview's viewport is not reported and one in a screen composable is`() {
        val locations = findings("DesignSystemRules.noLiteralsInFeatureUi").map { it.where }
        assertEquals(1, locations.size, locations.toString())
        assertTrue("ShopBadge.kt" in locations.single(), locations.single())
    }
}
