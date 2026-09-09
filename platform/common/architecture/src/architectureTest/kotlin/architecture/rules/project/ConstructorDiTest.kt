package architecture.rules.project

import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.Violation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dependency injection rules over `src/architectureTest/fixtures/constructor-di`: a
 * `feature.shop` server whose `ReconcileOrdersImpl` defaults a dependency and a setting, whose
 * `ReportsClient` is constructed in a binding lambda and defaults a setting, whose
 * `InvoicesClient` constructs its own `OrdersConfig`, and whose `OrdersRepository` takes the
 * clock and the configuration as required parameters; plus a platform module binding
 * `MetricsClient` through a lambda.
 */
class ConstructorDiTest {

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(File("src/architectureTest/fixtures/constructor-di").absolutePath) },
        membership = UkptArchitecture.membership,
    )

    private fun violations(ruleId: String): List<Violation> =
        run.violations(run.rules.first { it.id == ruleId })

    @Test
    fun `a lambda constructing an application class is reported in feature and platform modules`() {
        val names = violations("ProjectRules.constructorReferenceBindings")
            .map { it.message.substringAfter('`').substringBefore('`') }
            .sorted()
        assertEquals(listOf("MetricsClient", "ReportsClient"), names)
    }

    @Test
    fun `a default on a registered constructor is reported, a configuration's default is not`() {
        val parameters = violations("ProjectRules.injectableConstructorsHaveNoDefaults")
            .map { violation ->
                val (cls, param) = Regex("""`([^`]+)` gives constructor parameter `([^`]+)`""").find(violation.message)!!.destructured
                "$cls.$param"
            }
            .sorted()
        assertEquals(
            listOf("ReconcileOrdersImpl.cleanupTimeout", "ReconcileOrdersImpl.clock", "ReportsClient.engine"),
            parameters,
        )
    }

    @Test
    fun `a configuration constructed by its consumer is reported`() {
        val violation = violations("ServerData.Configuration.assembledAtTheCompositionBoundary").single()
        assertTrue(violation.where.endsWith("/data/InvoicesClient.kt"), violation.where)
    }
}
