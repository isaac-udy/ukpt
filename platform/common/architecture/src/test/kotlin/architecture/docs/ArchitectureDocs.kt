package architecture.docs

import architecture.registry.RuleGroup
import java.io.File

/** One generated documentation file, addressed relative to the architecture module root. */
data class GeneratedDoc(val relativePath: String, val content: String)

/**
 * Renders the complete generated doc set for [groups]:
 *
 *  - `README.md` from the `UkptArchitecture.md` template (entry point, `{{toc}}`)
 *  - `docs/<layer>.md` from each group's sidecar, markers expanded from the catalog
 *  - `docs/<name>.md` from each standalone source under `rules/`
 *  - `docs/rule-index.md` entirely from the catalog
 *
 * Any validation problem (unresolvable marker, undocumented construct/rule, stale prose id, broken
 * link) fails the render with every error listed — the golden test therefore refuses to regenerate
 * from broken sources.
 */
fun renderArchitectureDocs(groups: List<RuleGroup>, moduleRoot: File): List<GeneratedDoc> {
    val catalog = CatalogIndex(groups)
    val sources = DocSources.discover(moduleRoot, groups)
    val errors = mutableListOf<String>()

    val sidecarExpansions = sources.groupSidecars.map { (group, file) ->
        Triple(group, expandMarkers(file.readText(), catalog, sources.sourcePath(file), errors), sources.sourcePath(file))
    }
    val layerDocs = sources.groupSidecars.zip(sidecarExpansions) { (group, file), (_, expanded, _) ->
        GeneratedDoc("docs/${sources.outputName(group)}.md", banner(sources.sourcePath(file)) + expanded.content)
    }
    val standaloneExpansions = sources.standalone.map { file ->
        file to expandMarkers(file.readText(), catalog, sources.sourcePath(file), errors)
    }
    val standaloneDocs = standaloneExpansions.map { (file, expanded) ->
        GeneratedDoc("docs/${file.name}", banner(sources.sourcePath(file)) + expanded.content)
    }
    coverageChecks(
        sidecarExpansions,
        sidecarExpansions.map { it.second } + standaloneExpansions.map { it.second },
        errors,
    )
    val ruleIndex = GeneratedDoc("docs/rule-index.md", banner(sourcePath = null) + renderRuleIndexDoc(groups))

    val linked = layerDocs + standaloneDocs + ruleIndex
    val readme = GeneratedDoc(
        "README.md",
        banner(sources.sourcePath(sources.readmeTemplate)) + expandMarkers(
            sources.readmeTemplate.readText(),
            catalog,
            sources.sourcePath(sources.readmeTemplate),
            errors,
            toc = linked.map { it.relativePath to titleOf(it) },
        ).content,
    )

    val all = listOf(readme) + linked
    validateProseRuleIds(all, catalog, errors)
    validateLinks(all, moduleRoot, errors)
    check(errors.isEmpty()) {
        "Architecture doc generation failed:\n" + errors.joinToString("\n") { " - $it" }
    }
    return all
}

private fun banner(sourcePath: String?): String = buildString {
    appendLine("<!--")
    appendLine("  GENERATED FILE — do not edit.")
    when (sourcePath) {
        null -> appendLine("  Generated entirely from the rule catalog.")
        else -> appendLine("  Narrative source: $sourcePath (structured blocks come from the rule catalog).")
    }
    appendLine("  Regenerate: UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test")
    appendLine("-->")
    appendLine()
}
