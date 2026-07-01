package architecture.docs

import architecture.registry.RuleGroup
import architecture.registry.Status
import java.io.File
import java.nio.file.Paths

/**
 * Validation over the generated doc set: every construct/rule is documented, every prose rule id
 * resolves, and every relative link/anchor points at something that exists. All problems are
 * aggregated so one render reports every issue at once.
 */

/**
 * Every construct must be documented in its own group's sidecar; every group-level rule must be
 * rendered *somewhere* in the doc set (its own sidecar or a standalone doc like `exceptions.md`).
 */
internal fun coverageChecks(
    sidecars: List<Triple<RuleGroup, ExpandResult, String>>,
    allExpanded: List<ExpandResult>,
    errors: MutableList<String>,
) {
    sidecars.forEach { (group, expanded, where) ->
        group.constructs.filterNot { it.id in expanded.constructIds }.forEach {
            errors += "$where: construct `${it.id}` is not documented — add {{construct:${it.id}}}"
        }
    }
    val renderedRules = allExpanded.flatMap { it.ruleIds }.toSet()
    sidecars.forEach { (group, _, where) ->
        group.declaredRules
            .filter { it.status is Status.Active }
            .filterNot { it.id in renderedRules }
            .forEach { errors += "$where: rule `${it.id}` is not documented anywhere — add {{rules:${group.id}}} or {{rule:${it.id}}}" }
    }
}

/**
 * Any inline-code span shaped like a rule id whose first segment names a catalog group must resolve
 * to a real group/construct/rule id — this is what catches a renamed rule leaving stale prose behind.
 */
internal fun validateProseRuleIds(docs: List<GeneratedDoc>, catalog: CatalogIndex, errors: MutableList<String>) {
    val idLike = Regex("""[A-Za-z]+(?:\.[A-Za-z0-9]+)+""")
    docs.forEach { doc ->
        forEachProseLine(doc.content) { line ->
            codeSpan.findAll(line).forEach { span ->
                val text = span.groupValues[1]
                if (!idLike.matches(text)) return@forEach
                if (text.substringBefore('.') !in catalog.groupsById) return@forEach
                if (text !in catalog.knownIds) {
                    errors += "${doc.relativePath}: `$text` looks like a rule id but doesn't resolve to the catalog"
                }
            }
        }
    }
}

/** Relative links must resolve to a generated doc (with a real anchor) or a file in the module. */
internal fun validateLinks(docs: List<GeneratedDoc>, moduleRoot: File, errors: MutableList<String>) {
    val anchorsByPath = docs.associate { it.relativePath to anchorsOf(it.content) }
    val link = Regex("""\[[^\]]*]\(([^)\s]+)\)""")
    docs.forEach { doc ->
        val docDir = doc.relativePath.substringBeforeLast('/', "")
        forEachProseLine(doc.content) { line ->
            link.findAll(line).forEach { match ->
                val target = match.groupValues[1]
                if ("://" in target || target.startsWith("mailto:")) return@forEach
                val path = target.substringBefore('#')
                val anchor = target.substringAfter('#', "")
                val resolved = if (path.isEmpty()) doc.relativePath else normalize(docDir, path)
                val targetAnchors = anchorsByPath[resolved]
                when {
                    targetAnchors == null && !File(moduleRoot, resolved).exists() ->
                        errors += "${doc.relativePath}: broken link `$target`"
                    targetAnchors != null && anchor.isNotEmpty() && anchor !in targetAnchors ->
                        errors += "${doc.relativePath}: broken anchor `$target`"
                }
            }
        }
    }
}

/** The doc's title: its first heading. */
internal fun titleOf(doc: GeneratedDoc): String {
    var title: String? = null
    forEachProseLine(doc.content) { line ->
        if (title == null && line.startsWith("# ")) title = line.removePrefix("# ").trim()
    }
    return title ?: doc.relativePath
}

private val codeSpan = Regex("""`([^`]+)`""")

private fun normalize(fromDir: String, path: String): String {
    val resolved = if (fromDir.isEmpty()) Paths.get(path) else Paths.get(fromDir, path)
    return resolved.normalize().toString()
}

/** GitHub-style anchor slugs for every heading, with `-n` suffixes for duplicates. */
private fun anchorsOf(content: String): Set<String> {
    val counts = mutableMapOf<String, Int>()
    val anchors = mutableSetOf<String>()
    forEachProseLine(content) { line ->
        if (!line.startsWith("#")) return@forEachProseLine
        val text = line.trimStart('#')
        if (!text.startsWith(" ")) return@forEachProseLine
        val slug = githubAnchor(text.trim())
        val seen = counts.getOrDefault(slug, 0)
        counts[slug] = seen + 1
        anchors += if (seen == 0) slug else "$slug-$seen"
    }
    return anchors
}

private fun githubAnchor(heading: String): String = heading
    .lowercase()
    .replace(Regex("""[^\p{L}\p{N}\- ]"""), "")
    .replace(' ', '-')

/** Walk [content] line by line, skipping fenced code blocks. */
internal fun forEachProseLine(content: String, action: (String) -> Unit) {
    var inFence = false
    content.lineSequence().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
            return@forEach
        }
        if (!inFence) action(line)
    }
}
