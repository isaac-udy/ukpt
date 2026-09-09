package architecture.rules.shared

import com.lemonappdev.konsist.api.provider.KoNameProvider
import dev.isaacudy.udytils.architecture.Violation

/**
 * The advisory audits over a [DomainInterfaceGraph]. Each returns grouped findings: one
 * [Violation] per candidate, its declarations in the evidence lines, and a review question in the
 * message. The thresholds are sampling thresholds for review, not limits.
 */
internal object DomainInterfaceAudits {
    /** A group of interfaces from one provider whose only consumer is one class. */
    const val SOLE_CONSUMER_GROUP = 3

    /** A consumer injecting this many domain interfaces. */
    const val CONSUMER_FAN_IN = 6

    /** Mutations on one noun from one provider. */
    const val OPERATION_FAMILY = 3

    private val readPrefixes = listOf("Get", "FlowOf", "List", "Find", "Observe", "Search", "Count", "Has", "Is", "Load", "Fetch", "Query")

    private val mutationVerbs = listOf(
        "Create", "Update", "Delete", "Store", "Save", "Set", "Mark", "Add", "Remove", "Rename", "Insert",
        "Upsert", "Clear", "Reset", "Assign", "Revoke", "Record", "Complete", "Cancel", "Claim", "Release",
        "Lock", "Unlock", "Enable", "Disable", "Archive", "Restore", "Submit", "Retire", "Open", "Close",
        "Start", "Finish", "Fail", "Succeed", "Reap", "Consume", "Rotate", "Join", "Leave", "Select",
    )

    private fun DomainInterfaceNode.isRead(): Boolean = readPrefixes.any { name.startsWith(it) }

    private data class SoleConsumerGroup(val provider: String, val consumer: DomainInterfaceConsumer, val nodes: List<DomainInterfaceNode>)

    private fun soleConsumerGroups(graph: DomainInterfaceGraph): List<SoleConsumerGroup> =
        graph.nodes
            .filter { it.consumers.size == 1 && it.providers.size == 1 }
            .groupBy { it.providers.single() to it.consumers.single().fqn }
            .map { (key, nodes) -> SoleConsumerGroup(key.first, nodes.first().consumers.single(), nodes.sortedBy { it.name }) }
            .filter { it.nodes.size >= SOLE_CONSUMER_GROUP }
            .sortedWith(compareByDescending<SoleConsumerGroup> { it.nodes.size }.thenBy { it.consumer.className })

    /** Read-only groups with one consumer: the projection candidates. */
    fun readFamiliesWithOneConsumer(graph: DomainInterfaceGraph): List<Violation> =
        soleConsumerGroups(graph)
            .filter { group -> group.nodes.all { it.isRead() } }
            .map { group ->
                Violation(
                    where = group.consumer.className,
                    message = "${group.nodes.size} reads from `${group.provider}` have `${group.consumer.className}` as their only consumer, injected together. " +
                        "Could `${group.provider}` return one domain model that carries them? " +
                        "Check that they are collected together, and for independent callers, authorization, failure, and freshness, before combining.",
                    evidence = group.nodes.map { it.name },
                )
            }

    /** Groups with one consumer that mix reads and other operations. */
    fun mixedFamiliesWithOneConsumer(graph: DomainInterfaceGraph): List<Violation> =
        soleConsumerGroups(graph)
            .filterNot { group -> group.nodes.all { it.isRead() } }
            .map { group ->
                val (reads, others) = group.nodes.partition { it.isRead() }
                Violation(
                    where = group.consumer.className,
                    message = "${group.nodes.size} domain interfaces from `${group.provider}` have `${group.consumer.className}` as their only consumer. " +
                        "Which of them is a capability on its own? Reads a consumer needs together are one projection; mutations on one model with one return type are one update family.",
                    evidence = reads.map { "read `${it.name}`" } + others.map { "operation `${it.name}`" },
                )
            }

    /** Consumers injecting many domain interfaces, grouped by provider. */
    fun consumersWithHighFanIn(graph: DomainInterfaceGraph): List<Violation> {
        val byConsumer = graph.nodes
            .flatMap { node -> node.consumers.map { it to node } }
            .groupBy({ it.first.fqn }, { it.second })
        return byConsumer.values
            .filter { it.size >= CONSUMER_FAN_IN }
            .sortedByDescending { it.size }
            .map { nodes ->
                val consumer = nodes.first().consumers.first { c -> nodes.all { n -> n.consumers.any { it.fqn == c.fqn } } }
                val byProvider = nodes.groupBy { it.providers.joinToString("+").ifEmpty { "no provider" } }
                Violation(
                    where = consumer.className,
                    message = "injects ${nodes.size} domain interfaces. " +
                        "A workflow may coordinate many independent capabilities; a consumer that assembles one concept from several reads of one provider is a projection candidate.",
                    evidence = byProvider.entries
                        .sortedByDescending { it.value.size }
                        .map { (provider, group) -> "`$provider`: ${group.map { it.name }.sorted().joinToString(", ")}" },
                )
            }
    }

    /** `Get<Model><Part>` and `FlowOf<Model><Part>` names, where `Model` is a domain model. */
    fun partOfModelNames(graph: DomainInterfaceGraph, modelNames: Set<String>): List<Violation> =
        graph.nodes
            .sortedBy { it.name }
            .mapNotNull { node ->
                val rest = readPrefixes.firstOrNull { node.name.startsWith(it) }?.let { node.name.removePrefix(it) } ?: return@mapNotNull null
                val model = modelNames
                    .filter { rest.startsWith(it) && rest.length > it.length + 1 && rest[it.length].isUpperCase() }
                    .maxByOrNull { it.length } ?: return@mapNotNull null
                if (rest in modelNames) return@mapNotNull null
                val part = rest.removePrefix(model)
                Violation(
                    where = node.name,
                    message = "names `$part` of domain model `$model`. Can `$model`, or a projection of it, carry `$part` instead of a separate read?",
                )
            }

    /** Mutations on one noun from one provider that could be one update family. */
    fun operationFamilies(graph: DomainInterfaceGraph): List<Violation> =
        graph.nodes
            .filterNot { it.isRead() }
            .filter { it.providers.size == 1 }
            .mapNotNull { node ->
                val verb = mutationVerbs.firstOrNull { node.name.startsWith(it) && node.name.length > it.length } ?: return@mapNotNull null
                Triple(node.providers.single(), node.name.removePrefix(verb), node)
            }
            .groupBy { it.first to it.second }
            .filterValues { it.size >= OPERATION_FAMILY }
            .entries
            .sortedByDescending { it.value.size }
            .map { (key, entries) ->
                val (provider, noun) = key
                Violation(
                    where = "$provider $noun",
                    message = "${entries.size} mutations on `$noun` from `$provider`. " +
                        "If they share a return type, one `Update$noun` with a sealed `Update` and a default function per variant replaces them; a mutation with its own return type or authority stays separate.",
                    evidence = entries.map { it.third.name }.sorted(),
                )
            }

    /** Interfaces no production class injects. */
    fun interfacesWithoutConsumer(graph: DomainInterfaceGraph): List<Violation> =
        graph.nodes
            .filter { it.consumers.isEmpty() }
            .sortedBy { it.name }
            .map { node ->
                Violation(
                    where = "${node.name} (feature.${node.feature})",
                    message = "no class injects it. A consumer outside a constructor (an app-module lambda, a top-level function) is not counted; check before deleting.",
                )
            }

    /** Names of every domain model visible to the side, for [partOfModelNames]. */
    fun modelNames(models: List<com.lemonappdev.konsist.api.declaration.KoBaseDeclaration>): Set<String> =
        models.mapNotNull { (it as? KoNameProvider)?.name }.toSet()
}
