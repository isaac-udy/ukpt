package architecture.spike

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Demonstrator for the object-based registry spike — not an architecture check. Shows the three
 * things the redesign is meant to deliver: exact-name ids, reflection-based construct discovery,
 * and direct cross-layer references (no `Classifiers`).
 */
class SpikeRegistryTest {

    private val groups = listOf(DomainLayer, DataLayer)

    @Test
    fun ruleIdsUseExactObjectAndPropertyNames() {
        val ids = assemble(groups).map { it.id }.toSet()
        // construct rules: <Group>.<Construct>.<rule>
        assertTrue("DomainLayer.DomainInterface.errorsViaExceptions" in ids, ids.toString())
        assertTrue("DomainLayer.DomainObject.immutable" in ids, ids.toString())
        assertTrue("DataLayer.Repository.propertiesEagerlyInitialized" in ids, ids.toString())
        // group-level rule: <Group>.<rule>
        assertTrue("DataLayer.noUiDeps" in ids, ids.toString())
    }

    @Test
    fun constructsAreDiscoveredByReflection() {
        assertEquals(
            setOf("DomainLayer.DomainInterface", "DomainLayer.DomainObject", "DomainLayer.UseCase"),
            DomainLayer.constructs.map { it.id }.toSet(),
        )
    }

    @Test
    fun crossLayerReferenceIsDirect() {
        assemble(groups) // attaches the package gates the constructs classify with
        // DataLayer.Repository's classification reuses DomainLayer.DomainInterface.test(...) directly.
        assertTrue(DataLayer.Repository.requirements.any { it.description.contains("domain interface") })
        // And the referenced classifier is a real, importable construct — the whole point of objects.
        assertEquals("DomainLayer.DomainInterface", DomainLayer.DomainInterface.id)
    }

    @Test
    fun dumpIndex() {
        println("\n--- spike rule index ---")
        assemble(groups).forEach { println("${it.tag.marker.padEnd(12)} ${it.id} — ${it.title}") }
        println("--- constructs (requirements) ---")
        groups.flatMap { it.constructs }.forEach { c ->
            println("${c.id}: " + c.requirements.joinToString(" AND ") { it.description })
        }
    }
}
