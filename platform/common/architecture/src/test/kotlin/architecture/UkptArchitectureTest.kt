package architecture

import architecture.registry.ArchitectureRun
import architecture.rules.UkptArchitecture
import architecture.testing.architectureGroupNodes
import architecture.testing.architectureMembershipNodes
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory

/**
 * The architecture rules: one nested test per enforced rule (`UkptArchitectureTest › <Group> ›
 * <Construct> › <rule>`), one `@TestFactory` per group so the groups hang directly off this class.
 * All factories share a single [ArchitectureRun] (one Konsist scope + module-graph scan).
 *
 * Adding a group to [UkptArchitecture] requires a matching factory here —
 * [RegistryIntegrityTest.everyGroupHasATestFactory] fails otherwise. Rules with audits appear as
 * `<rule> [audit]` tests that always pass but report their findings.
 */
class UkptArchitectureTest {

    @TestFactory
    @DisplayName("ModuleRules")
    fun moduleRules(): List<DynamicNode> = architectureGroupNodes(run, "ModuleRules")

    @TestFactory
    @DisplayName("DomainLayer")
    fun domainLayer(): List<DynamicNode> = architectureGroupNodes(run, "DomainLayer")

    @TestFactory
    @DisplayName("UiLayer")
    fun uiLayer(): List<DynamicNode> = architectureGroupNodes(run, "UiLayer")

    @TestFactory
    @DisplayName("DataLayer")
    fun dataLayer(): List<DynamicNode> = architectureGroupNodes(run, "DataLayer")

    @TestFactory
    @DisplayName("ServicesLayer")
    fun servicesLayer(): List<DynamicNode> = architectureGroupNodes(run, "ServicesLayer")

    @TestFactory
    @DisplayName("FeatureRules")
    fun featureRules(): List<DynamicNode> = architectureGroupNodes(run, "FeatureRules")

    @TestFactory
    @DisplayName("ProjectRules")
    fun projectRules(): List<DynamicNode> = architectureGroupNodes(run, "ProjectRules")

    /** The cross-layer membership rule (`architecture.everyDeclarationBelongsToALayer`). */
    @TestFactory
    @DisplayName("membership")
    fun membership(): List<DynamicNode> = architectureMembershipNodes(run)

    companion object {
        /** One Konsist scope + module-graph scan shared by every factory. */
        private val run by lazy { ArchitectureRun(UkptArchitecture) }
    }
}
