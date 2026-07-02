package architecture

import architecture.rules.UkptArchitecture
import architecture.registry.ArchitectureRun
import architecture.registry.Describe
import architecture.registry.ModuleGraphConstraint
import architecture.registry.Rule
import architecture.registry.RuleGroup
import architecture.registry.ScopeConstraint
import architecture.registry.Violation
import architecture.registry.verify
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The architecture rules. [architecture] reports **every enforced rule as its own nested test**
 * (`layer › construct › rule`) off a single Konsist scan; [ArchitectureDocsTest] keeps the
 * generated docs in lock-step with the catalog.
 */
class RegistryArchitectureTest {

    /**
     * One dynamic test per enforced rule, nested `<Layer> › <Construct> › <rule>`, all sharing one
     * [ArchitectureRun] (a single scope + module-graph scan). Only rules that actually execute a check
     * (`✅ tested`) appear; `📋 guidance` / `⚙️ codegen` are documented in the index, not run here.
     */
    @TestFactory
    fun architecture(): List<DynamicNode> {
        val run = ArchitectureRun(UkptArchitecture.all)

        fun runs(rule: Rule) = rule.enforcement is ScopeConstraint || rule.enforcement is ModuleGraphConstraint
        fun leaf(rule: Rule) = dynamicTest(rule.id.substringAfterLast('.')) {
            val violations = run.violations(rule)
            if (violations.isNotEmpty()) {
                fail("[${rule.id}] ${rule.title}\n" + violations.joinToString("\n") { "  - ${it.where}: ${it.message}" })
            }
        }

        val (membership, layered) = run.rules.filter(::runs).partition { it.id.startsWith("architecture.") }
        return buildList {
            layered.groupBy { it.id.substringBefore('.') }.forEach { (groupId, groupRules) ->
                val (constructRules, groupLevel) = groupRules.partition { r -> r.id.count { it == '.' } == 2 }
                add(
                    dynamicContainer(
                        groupId,
                        buildList<DynamicNode> {
                            constructRules.groupBy { it.id.substringBeforeLast('.') }.forEach { (constructId, rules) ->
                                add(dynamicContainer(constructId.substringAfterLast('.'), rules.map(::leaf)))
                            }
                            groupLevel.forEach { add(leaf(it)) }
                        },
                    ),
                )
            }
            membership.forEach { add(leaf(it)) }
        }
    }

    /**
     * Self-check: proves the runner actually detects and reports violations (so a green
     * [architecture] run means "no violations", not "nothing ran"), and that the module-graph
     * provider parses real edges (so the module rules aren't passing vacuously).
     */
    @Test
    fun runnerDetectsViolationsAndParsesGraph() {
        val error = assertFailsWith<Throwable> { verify(listOf(Sentinel)) }
        val message = error.message.orEmpty()
        assertTrue("Sentinel.alwaysFails" in message, "the runner should report the violation by id; was:\n$message")
        assertTrue("Sentinel.graphParses" !in message, "the module graph should parse real edges; was:\n$message")
    }
}

/** Sentinel group for [RegistryArchitectureTest.runnerDetectsViolationsAndParsesGraph]. */
private object Sentinel : RuleGroup() {
    @Suppress("unused")
    @Describe("Always reports a violation")
    val alwaysFails by rule {
        scope { _, _ -> listOf(Violation("sentinel", "intentional self-check violation")) }
    }

    @Suppress("unused")
    @Describe("The module graph parses at least one module edge")
    val graphParses by rule {
        moduleGraph { graph, _ ->
            if (graph.edges.isEmpty()) listOf(Violation("graph", "no module edges were parsed")) else emptyList()
        }
    }
}
