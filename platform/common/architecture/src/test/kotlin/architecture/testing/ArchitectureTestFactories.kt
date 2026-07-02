package architecture.testing

import architecture.registry.ArchitectureRun
import architecture.registry.ModuleGraphConstraint
import architecture.registry.NotEnforced
import architecture.registry.Rule
import architecture.registry.ScopeConstraint
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import kotlin.test.fail

/**
 * Builders for the consumer's architecture test class — one `@TestFactory` per group keeps the
 * groups directly under the test class in the JUnit tree:
 *
 *     class MyArchitectureTest {
 *         @TestFactory @DisplayName("DomainLayer")
 *         fun domainLayer() = architectureGroupNodes(run, "DomainLayer")
 *         …
 *         companion object { private val run by lazy { ArchitectureRun(MyArchitecture) } }
 *     }
 *
 * Rules with audits appear as `<rule> [audit]` tests that always pass but print their findings.
 */
fun architectureGroupNodes(run: ArchitectureRun, groupId: String): List<DynamicNode> {
    val groupRules = run.rules.filter { (runs(it) || audited(it)) && it.id.startsWith("$groupId.") }
    val (constructRules, groupLevel) = groupRules.partition { r -> r.id.count { it == '.' } == 2 }
    return buildList {
        constructRules.groupBy { it.id.substringBeforeLast('.') }.forEach { (constructId, rules) ->
            add(dynamicContainer(constructId.substringAfterLast('.'), rules.map { leaf(run, it) }))
        }
        groupLevel.forEach { add(leaf(run, it)) }
    }
}

/** The cross-layer membership rule (present only when the definition provides a membership universe). */
fun architectureMembershipNodes(run: ArchitectureRun): List<DynamicNode> = run.rules
    .filter { (runs(it) || audited(it)) && it.id.startsWith("architecture.") }
    .map { leaf(run, it) }

private fun leaf(run: ArchitectureRun, rule: Rule): DynamicNode = if (audited(rule)) {
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
