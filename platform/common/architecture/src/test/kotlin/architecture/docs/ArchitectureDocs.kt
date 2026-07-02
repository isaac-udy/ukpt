package architecture.docs

import architecture.registry.RuleGroup
import java.io.File

/** One generated documentation file, addressed relative to the architecture module root. */
data class GeneratedDoc(val relativePath: String, val content: String)

/**
 * Renders the complete generated doc set for [groups]:
 *
 *  - `docs/<layer>.md` — compiled per layer from its fragments + the catalog ([renderLayerDoc])
 *  - `docs/<name>.md` — each standalone source under `rules/`, markers expanded
 *  - `docs/rule-index.md` — entirely from the catalog
 *  - `README.md` — from the `UkptArchitecture.md` template (entry point, `{{toc}}`)
 *
 * Any validation problem (unresolvable marker or fragment, stale prose id, broken link) fails the
 * render with every error listed — the golden test therefore refuses to regenerate from broken
 * sources.
 */
fun renderArchitectureDocs(groups: List<RuleGroup>, moduleRoot: File): List<GeneratedDoc> {
    val catalog = CatalogIndex(groups)
    val sources = DocSources.discover(moduleRoot, groups)
    val errors = mutableListOf<String>()

    val layerDocs = sources.layers.map { layer ->
        val content = renderLayerDoc(layer, sources::sourcePath, errors)
        val note = "Sources: @Describe annotations in the Kotlin catalog in `${sources.packageDirPath(layer.group)}/` " +
            "(narrative + rules), plus the `*.examples.md` files beside it."
        GeneratedDoc("docs/${sources.outputName(layer.group)}.md", banner(note) + content)
    }
    val standaloneDocs = sources.standalone.map { file ->
        val note = "Narrative source: `${sources.sourcePath(file)}`; rule content comes from the rule catalog."
        GeneratedDoc("docs/${file.name}", banner(note) + expandMarkers(file.readText(), catalog, sources.sourcePath(file), errors))
    }
    val ruleIndex = GeneratedDoc(
        "docs/rule-index.md",
        banner("Generated entirely from the rule catalog.") + renderRuleIndexDoc(groups),
    )

    val linked = layerDocs + standaloneDocs + ruleIndex
    val readme = GeneratedDoc(
        "README.md",
        banner("Narrative source: `${sources.sourcePath(sources.readmeTemplate)}`.") + expandMarkers(
            sources.readmeTemplate.readText(),
            catalog,
            sources.sourcePath(sources.readmeTemplate),
            errors,
            toc = linked.map { it.relativePath to titleOf(it) },
        ),
    )

    val all = listOf(readme) + linked
    validateProseRuleIds(all, catalog, errors)
    validateLinks(all, moduleRoot, errors)
    check(errors.isEmpty()) {
        "Architecture doc generation failed:\n" + errors.joinToString("\n") { " - $it" }
    }
    return all
}

/** GitHub-style note alert, placed above the document title. */
private fun banner(sourceNote: String): String = buildString {
    appendLine("> [!NOTE]")
    appendLine("> **This file is generated — do not edit it by hand.**")
    appendLine("> $sourceNote")
    appendLine("> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.")
    appendLine()
}
