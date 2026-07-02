package architecture.docs

import architecture.registry.ArchitectureDefinition
import java.io.File

/** One generated documentation file, addressed relative to the architecture module root. */
data class GeneratedDoc(val relativePath: String, val content: String)

/**
 * Renders the complete generated doc set for [definition]:
 *
 *  - `<outputDir>/<layer>.md` — compiled per layer from `@Describe` + examples files ([renderLayerDoc])
 *  - `<outputDir>/<name>.md` — each standalone source in the catalog root, markers expanded
 *  - `<outputDir>/rule-index.md` — entirely from the catalog
 *  - `README.md` — from the definition object's `@Describe` text (`{{toc}}` expands)
 *
 * Any validation problem (unresolvable marker or fragment, stale prose id, broken link) fails the
 * render with every error listed — the golden test therefore refuses to regenerate from broken
 * sources.
 */
fun renderArchitectureDocs(definition: ArchitectureDefinition, moduleRoot: File): List<GeneratedDoc> {
    val config = definition.docs
    val catalog = CatalogIndex(definition)
    val sources = DocSources.discover(moduleRoot, definition)
    val errors = mutableListOf<String>()
    val sourceLinkBase = "../".repeat(config.outputDir.split('/').size).dropLast(1) + "/" + config.sourceRoot
    val regenerate = "Regenerate with `${config.regenerateCommand}`."

    val layerDocs = sources.layers.map { layer ->
        val content = renderLayerDoc(layer, sources::sourcePath, sourceLinkBase, errors)
        val note = "Sources: @Describe annotations in the Kotlin catalog in `${sources.packageDirPath(layer.group)}/` " +
            "(narrative + rules), plus the `*.examples.md` files beside it."
        GeneratedDoc("${config.outputDir}/${sources.outputName(layer.group)}.md", banner(note, regenerate) + content)
    }
    val standaloneDocs = sources.standalone.map { file ->
        val note = "Narrative source: `${sources.sourcePath(file)}`; rule content comes from the rule catalog."
        GeneratedDoc(
            "${config.outputDir}/${file.name}",
            banner(note, regenerate) + expandMarkers(file.readText(), catalog, sources.sourcePath(file), errors),
        )
    }
    val ruleIndex = GeneratedDoc(
        "${config.outputDir}/rule-index.md",
        banner("Generated entirely from the rule catalog.", regenerate) + renderRuleIndexDoc(definition, sourceLinkBase),
    )

    val definitionPath = "${config.sourceRoot}/${definition.javaClass.packageName.replace('.', '/')}/${definition.name}.kt"
    val linked = layerDocs + standaloneDocs + ruleIndex
    val readme = GeneratedDoc(
        "README.md",
        banner("Source: the @Describe annotation on `${definition.name}` (`$definitionPath`).", regenerate) + expandMarkers(
            definition.readme,
            catalog,
            "README template (@Describe on ${definition.name})",
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
private fun banner(sourceNote: String, regenerate: String): String = buildString {
    appendLine("> [!NOTE]")
    appendLine("> **This file is generated — do not edit it by hand.**")
    appendLine("> $sourceNote")
    appendLine("> $regenerate")
    appendLine()
}
