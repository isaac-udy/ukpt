package architecture.spike

import kotlin.test.Test

/** All groups in the object-based catalog, in document order. */
object UkptSpike {
    val all = listOf(ModuleRules, DomainLayer, UiLayer, DataLayer, ServicesLayer, FeatureRules, ProjectRules)
}

/**
 * Cross-check for the object-engine port: runs the full object catalog over the real codebase. It
 * must agree with the live `RegistryArchitectureTest.architecture()` (both green) before the cutover.
 */
class SpikeRegistryTest {

    @Test
    fun architecture() = verify(UkptSpike.all)
}
