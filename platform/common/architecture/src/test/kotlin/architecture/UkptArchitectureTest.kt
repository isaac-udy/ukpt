package architecture

import architecture.registry.ArchitectureRun
import architecture.registry.ModuleGraphConstraint
import architecture.registry.NotEnforced
import architecture.registry.Rule
import architecture.registry.ScopeConstraint
import architecture.rules.UkptArchitecture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.fail

/**
 * The architecture rules: one nested test per enforced rule (`UkptArchitectureTest › <Group> ›
 * <Construct> › <rule>`), one `@TestFactory` per group so the groups hang directly off this class.
 * All factories share a single [ArchitectureRun] (one Konsist scope + module-graph scan).
 *
 * Adding a group to [UkptArchitecture.all] requires a matching factory here —
 * [RegistryIntegrityTest.everyGroupHasATestFactory] fails otherwise. Guidance/unverifiable rules
 * with audits appear as `<rule> [audit]` tests that always pass but report their findings.
 */
class UkptArchitectureTest {

    @TestFactory
    @DisplayName("ModuleRules")
    fun moduleRules(): List<DynamicNode> = groupNodes("ModuleRules")

    @TestFactory
    @DisplayName("DomainLayer")
    fun domainLayer(): List<DynamicNode> = groupNodes("DomainLayer")

    @TestFactory
    @DisplayName("UiLayer")
    fun uiLayer(): List<DynamicNode> = groupNodes("UiLayer")

    @TestFactory
    @DisplayName("DataLayer")
    fun dataLayer(): List<DynamicNode> = groupNodes("DataLayer")

    @TestFactory
    @DisplayName("ServicesLayer")
    fun servicesLayer(): List<DynamicNode> = groupNodes("ServicesLayer")

    @TestFactory
    @DisplayName("FeatureRules")
    fun featureRules(): List<DynamicNode> = groupNodes("FeatureRules")

    @TestFactory
    @DisplayName("ProjectRules")
    fun projectRules(): List<DynamicNode> = groupNodes("ProjectRules")

    /** The cross-layer membership rule (`architecture.everyDeclarationBelongsToALayer`). */
    @TestFactory
    @DisplayName("membership")
    fun membership(): List<DynamicNode> = run.rules
        .filter { (runs(it) || audited(it)) && it.id.startsWith("architecture.") }
        .map(::leaf)

    private fun groupNodes(groupId: String): List<DynamicNode> {
        val groupRules = run.rules.filter { (runs(it) || audited(it)) && it.id.startsWith("$groupId.") }
        val (constructRules, groupLevel) = groupRules.partition { r -> r.id.count { it == '.' } == 2 }
        return buildList {
            constructRules.groupBy { it.id.substringBeforeLast('.') }.forEach { (constructId, rules) ->
                add(dynamicContainer(constructId.substringAfterLast('.'), rules.map(::leaf)))
            }
            groupLevel.forEach { add(leaf(it)) }
        }
    }

    private fun leaf(rule: Rule): DynamicNode = if (audited(rule)) {
        // An audit: never fails, but reports where the convention is not being followed.
        dynamicTest("${rule.id.substringAfterLast('.')} [audit]") {
            val findings = run.auditFindings(rule)
            if (findings.isNotEmpty()) {
                println("[audit] ${rule.id} — not followed in ${findings.size} place(s):")
                findings.forEach { println("  - ${it.where}: ${it.message}") }
            }
        }
    } else {
        dynamicTest(rule.id.substringAfterLast('.')) {
            val violations = run.violations(rule)
            if (violations.isNotEmpty()) {
                fail("[${rule.id}] ${rule.title}\n" + violations.joinToString("\n") { "  - ${it.where}: ${it.message}" })
            }
        }
    }

    private fun runs(rule: Rule) = rule.enforcement is ScopeConstraint || rule.enforcement is ModuleGraphConstraint
    private fun audited(rule: Rule) = (rule.enforcement as? NotEnforced)?.audit != null

    companion object {
        /** One Konsist scope + module-graph scan shared by every factory. */
        private val run by lazy { ArchitectureRun(UkptArchitecture.all) }
    }
}
