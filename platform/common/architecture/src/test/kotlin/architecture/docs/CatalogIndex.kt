package architecture.docs

import architecture.registry.Construct
import architecture.registry.Rule
import architecture.registry.RuleGroup
import architecture.registry.exhaustiveRule
import architecture.registry.membershipRule
import architecture.registry.prepare

/**
 * Id-indexed view of the catalog for doc generation: groups, constructs, and every rule the engine
 * enforces (including the derived exhaustiveness + membership rules), so markers and prose ids can
 * be resolved and validated.
 */
internal class CatalogIndex(val groups: List<RuleGroup>) {
    init {
        prepare(groups)
    }

    val groupsById: Map<String, RuleGroup> = groups.associateBy { it.id }
    val constructsById: Map<String, Construct> = groups.flatMap { it.constructs }.associateBy { it.id }
    val rulesById: Map<String, Rule> = buildMap {
        groups.forEach { group ->
            group.declaredRules.forEach { put(it.id, it) }
            group.constructs.flatMap { it.declaredRules }.forEach { put(it.id, it) }
            if (group.inPackage != null) exhaustiveRule(group).let { put(it.id, it) }
        }
        membershipRule(groups).let { put(it.id, it) }
    }

    /** Every id a doc may legitimately reference in prose. */
    val knownIds: Set<String> = groupsById.keys + constructsById.keys + rulesById.keys
}
