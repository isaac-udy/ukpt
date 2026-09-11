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
 * `ProjectRules.noBackingProperties` over `src/architectureTest/fixtures/backing-properties`: a
 * `feature.shop` client whose `CartStore` backs `items` and `events` with `_`-prefixed
 * properties and holds an unpaired `_scratch`, whose file backs a top-level `registry`, whose
 * `PromoStore` uses an explicit backing field and a `private set`, and whose nested
 * `PromoStore.Defaults` backs `limits`.
 */
class BackingPropertiesTest {

    private val fixtures = File("src/architectureTest/fixtures/backing-properties").absolutePath

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(fixtures) },
        membership = UkptArchitecture.membership,
    )

    private fun violations(ruleId: String): List<Violation> =
        run.violations(run.rules.first { it.id == ruleId })

    @Test
    fun `a backing property beside the property it backs is reported in classes, nested objects, and files`() {
        val names = violations("ProjectRules.noBackingProperties")
            .map { it.message.substringAfter('`').substringBefore('`') }
            .sorted()
        assertEquals(listOf("_events", "_items", "_limits", "_registry"), names)
    }

    @Test
    fun `an explicit backing field is parsed as one property`() {
        val promoStore = Konsist.scopeFromExternalDirectory(fixtures)
            .classes(includeNested = false)
            .first { it.name == "PromoStore" }
        val names = promoStore.properties(includeNested = false).map { it.name }
        assertEquals(listOf("codes", "lastAppliedAt"), names)
        val codes = promoStore.properties(includeNested = false).first { it.name == "codes" }
        assertTrue(codes.text.contains("field = mutableListOf()"), codes.text)
        assertEquals("List<String>", codes.type?.name)
    }
}
