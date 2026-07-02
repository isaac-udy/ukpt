package architecture.docs

import java.io.File
import java.nio.file.Paths

/**
 * Validation over the generated doc set: every prose rule id resolves, and every relative
 * link/anchor points at something that exists. (Doc *coverage* needs no check — the compiled layer
 * structure renders every construct and rule by construction.) All problems are aggregated so one
 * render reports every issue at once.
 */

/**
 * Any inline-code span shaped like a rule id whose first segment names a catalog group must resolve
 * to a real group/construct/rule id — this is what catches a renamed rule leaving stale prose behind.
 * Spans ending in `.md`/`.kt` are filename mentions, not ids.
 */
internal fun validateProseRuleIds(docs: List<GeneratedDoc>, catalog: CatalogIndex, errors: MutableList<String>) {
    val idLike = Regex("""[A-Za-z]+(?:\.[A-Za-z0-9]+)+""")
    docs.forEach { doc ->
        forEachProseLine(doc.content) { line ->
            codeSpan.findAll(line).forEach { span ->
                val text = span.groupValues[1]
                if (!idLike.matches(text) || text.endsWith(".md") || text.endsWith(".kt")) return@forEach
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

internal fun githubAnchor(heading: String): String = heading
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
