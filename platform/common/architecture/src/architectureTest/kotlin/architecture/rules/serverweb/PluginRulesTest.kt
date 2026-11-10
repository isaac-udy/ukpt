package architecture.rules.serverweb

import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Plugin rules over `src/architectureTest/fixtures/web-plugins`: `SessionPlugin` is internal,
 * implements `ServerPlugin` and takes a domain interface, so it stays quiet; each other class
 * breaks one rule.
 */
class PluginRulesTest {

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(File("src/architectureTest/fixtures/web-plugins").absolutePath) },
        membership = UkptArchitecture.membership,
    )

    private fun reported(ruleId: String): List<String> =
        run.violations(run.rules.first { it.id == ruleId }).map { it.message.substringAfter('`').substringBefore('`') }

    @Test
    fun `a public plugin is reported`() {
        assertEquals(listOf("PublicPlugin"), reported("ServerWeb.Plugin.internalVisibility"))
    }

    @Test
    fun `a plugin that does not implement ServerPlugin is reported`() {
        assertEquals(listOf("DetachedPlugin"), reported("ServerWeb.Plugin.implementsServerPlugin"))
    }

    @Test
    fun `a plugin that injects a repository is reported`() {
        assertEquals(listOf("StoragePlugin"), reported("ServerWeb.Plugin.noPersistenceInjection"))
    }

    @Test
    fun `every plugin is a Construct of the web layer`() {
        assertEquals(emptyList(), reported("ServerWeb.exhaustive"))
    }
}
