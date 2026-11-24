package ukpt.coverage

import java.io.File

/**
 * The HTML report: `index.html`, one page per Construct, feature and module listing its
 * declarations, and one page per source file with each measured line marked.
 */
class HtmlReport(private val report: CoverageReport, private val root: File, private val outputDir: File) {

    private val targetsByPath = report.targets.groupBy { it.path }

    fun write() {
        outputDir.resolve("report.css").writeText(CSS)
        write("index.html", "Test coverage", depth = 0, indexBody())
        constructGroups().forEach { (construct, targets) ->
            write(constructPage(construct), construct, depth = 1, listingBody(construct, targets, report.classification.constructs[construct]?.expectation))
        }
        report.targets.filter { it.module.feature != null }.groupBy { it.module.feature!! }.forEach { (feature, targets) ->
            write(featurePage(feature), "Feature $feature", depth = 1, featureBody(feature, targets))
        }
        report.targets.filter { it.module.feature == null }.groupBy { it.module }.forEach { (module, targets) ->
            write(modulePage(module.path), module.path, depth = 1, listingBody(module.path, targets, module.expectation, subtitle = "Lines no Construct claims follow the module expectation."))
        }
        targetsByPath.forEach { (path, targets) -> write(filePage(path), path.substringAfterLast('/'), depth = 1, fileBody(path, targets)) }
    }

    private fun write(name: String, title: String, depth: Int, body: String) {
        val file = outputDir.resolve(name)
        file.parentFile.mkdirs()
        val up = "../".repeat(depth)
        file.writeText(
            """
            |<!doctype html>
            |<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            |<title>${esc(title)} · Test coverage</title><link rel="stylesheet" href="${up}report.css"></head>
            |<body><main>
            |${if (depth > 0) "<nav><a href=\"${up}index.html\">Test coverage</a></nav>" else ""}
            |$body
            |</main></body></html>
            """.trimMargin(),
        )
    }

    // Index

    private fun indexBody(): String = buildString {
        val assessed = report.targets.filter { it.assessed }
        val total = assessed.tally()
        append("<h1>Test coverage</h1>")
        append(
            "<p class=\"lede\">Each line counts as <b>covered</b> when a test from a category its Construct expects runs it, and " +
                "<b>incidental</b> when only tests from other categories run it. End-to-end Tests are not measured.</p>",
        )
        append("<div class=\"tiles\">")
        append(tile("Covered", total.covered, total.assessed, "c"))
        append(tile("Incidental", total.incidental, total.assessed, "i"))
        append(tile("Uncovered", total.uncovered, total.assessed, "u"))
        append("</div>")
        append("<p class=\"muted\">${total.assessed} lines are expected to be tested. ${bar(total)}</p>")

        append(section("By Construct", "Declarations grouped by the Construct they match. Lines in feature modules only; module expectations are listed under Modules."))
        append(constructTable())

        append(section("By feature", "Each feature's lines by layer. A feature's page lists its declarations."))
        append(featureTable())

        append(section("Modules", "Modules outside <code>:feature</code>. A module expectation in the root <code>testCoverage { }</code> block applies to lines that no Construct claims."))
        append(moduleTable())

        append(section("Gaps", "Declarations with the most lines that no expected test runs, most first. Untested lines opens the first such line."))
        append(gapTable(assessed.filter { it.tally.covered < it.tally.assessed }.sortedByDescending { it.tally.assessed - it.tally.covered }.take(20), depth = 0))

        val unclassified = report.targets.filter { it.basis == Basis.Unclassified }
        if (unclassified.isNotEmpty()) {
            append(section("Unclassified", "Feature lines in no declaration that matches a Construct, such as private top-level helpers. They are not in the totals."))
            append(unassessedTable(unclassified, depth = 0))
        }
        val noExpectation = report.targets.filter { it.basis == Basis.NoExpectation }
        if (noExpectation.isNotEmpty()) {
            append(section("No expectation", "Lines in modules without a module expectation. Declare one in the root <code>testCoverage { }</code> block."))
            append(unassessedTable(noExpectation, depth = 0))
        }
        val excluded = report.targets.filter { it.expectation is Expectation.None }
        if (excluded.isNotEmpty()) {
            append(section("Not expected to be tested", "Lines whose Construct or module expects no tests. They are not in the totals."))
            append(excludedTable(excluded))
        }
        if (report.notMeasured.isNotEmpty() || report.generatedFiles.isNotEmpty() || report.objectHeaderLines > 0) {
            append(section("Not measured", "Kover measures JVM and Android host tests only. These source sets compile for other hosts and have no coverage figures."))
            append(table(listOf("Module", "Source set", "Files"), report.notMeasured.map { listOf(code(it.module), code(it.sourceSet), num(it.files)) }))
            if (report.objectHeaderLines > 0) {
                append("<p class=\"muted\">${report.objectHeaderLines} lines that open an <code>object</code> body are left out: Kover attributes the object's creation to that line, and only code that reads the object itself creates the object.</p>")
            }
            if (report.generatedFiles.isNotEmpty()) {
                append("<details><summary>${report.generatedFiles.size} generated source files are left out</summary><ul>${report.generatedFiles.joinToString("") { "<li>${code(it)}</li>" }}</ul></details>")
            }
        }
        if (report.problems.isNotEmpty()) {
            append(section("Report problems", "Coverage data the report could not attribute."))
            append("<ul class=\"problems\">${report.problems.joinToString("") { "<li>${inlineCode(it)}</li>" }}</ul>")
        }
    }

    private fun constructGroups(): Map<String, List<Target>> =
        report.targets.filter { it.basis == Basis.Construct && it.module.feature != null }
            .flatMap { target -> target.constructs.map { it to target } }
            .groupBy({ it.first }, { it.second })

    private fun constructTable(): String {
        val groups = constructGroups()
        val order = report.classification.constructs.keys.toList()
        val rows = mutableListOf<String>()
        groups.keys.sortedBy { order.indexOf(it) }.groupBy { it.substringBefore('.') }.forEach { (group, constructs) ->
            val groupTargets = constructs.flatMap { groups.getValue(it) }.distinct()
            rows += "<tr class=\"group\"><th colspan=\"3\">${esc(group)}</th>${tallyCells(groupTargets.filter { it.assessed }.tally())}</tr>"
            constructs.forEach { construct ->
                val targets = groups.getValue(construct)
                val expectation = report.classification.constructs[construct]?.expectation ?: Expectation.Unspecified
                val tally = targets.tally()
                rows += "<tr${fadedClass(expectation is Expectation.AnyOf)}><td><a href=\"${constructPage(construct)}\">${esc(construct.substringAfter('.'))}</a></td>" +
                    "<td>${expectationCell(expectation)}</td><td class=\"n\">${targets.size}</td>${tallyCells(shown(tally))}</tr>"
            }
        }
        return "<table><thead><tr><th>Construct</th><th>Expected</th><th class=\"n\">Decls</th><th class=\"n\">Lines</th><th class=\"barcol\"></th>" +
            "<th class=\"n\">Covered</th><th class=\"n\">Incidental</th><th class=\"n\">Uncovered</th></tr></thead><tbody>${rows.joinToString("")}</tbody></table>"
    }

    private fun featureTable(): String {
        val rows = mutableListOf<String>()
        report.targets.filter { it.module.feature != null }.groupBy { it.module.feature!! }.toSortedMap().forEach { (feature, targets) ->
            rows += "<tr class=\"group\"><th colspan=\"2\"><a href=\"${featurePage(feature)}\">${esc(feature)}</a></th>${tallyCells(targets.filter { it.assessed }.tally())}</tr>"
            targets.filter { it.assessed }.groupBy { layerOf(it) }.toSortedMap().forEach { (layer, layerTargets) ->
                rows += "<tr><td>${esc(layer)}</td><td class=\"n\">${layerTargets.size}</td>${tallyCells(layerTargets.tally())}</tr>"
            }
        }
        return "<table><thead><tr><th>Feature / layer</th><th class=\"n\">Decls</th><th class=\"n\">Lines</th><th class=\"barcol\"></th>" +
            "<th class=\"n\">Covered</th><th class=\"n\">Incidental</th><th class=\"n\">Uncovered</th></tr></thead><tbody>${rows.joinToString("")}</tbody></table>"
    }

    private fun moduleTable(): String {
        val rows = report.targets.filter { it.module.feature == null }.groupBy { it.module }.toSortedMap(compareBy { it.path }).map { (module, targets) ->
            val assessed = targets.filter { it.assessed }
            val expectation = module.expectation
            "<tr${fadedClass(assessed.isNotEmpty())}><td><a href=\"${modulePage(module.path)}\">${esc(module.path)}</a></td>" +
                "<td>${if (expectation == null) "<span class=\"warn\">None declared</span>" else expectationCell(expectation)}</td>" +
                tallyCells(if (assessed.isEmpty()) shown(targets.tally()) else assessed.tally()) + "</tr>"
        }
        return "<table><thead><tr><th>Module</th><th>Expected</th><th class=\"n\">Lines</th><th class=\"barcol\"></th>" +
            "<th class=\"n\">Covered</th><th class=\"n\">Incidental</th><th class=\"n\">Uncovered</th></tr></thead><tbody>${rows.joinToString("")}</tbody></table>"
    }

    private fun gapTable(targets: List<Target>, depth: Int): String {
        if (targets.isEmpty()) return "<p class=\"muted\">Every line expected to be tested is covered.</p>"
        val up = "../".repeat(depth)
        val rows = targets.map { target ->
            val tally = target.tally
            val untested = (target.linesIn(LineState.Uncovered) + target.linesIn(LineState.Incidental)).sorted()
            val incidental = target.linesIn(LineState.Incidental)
            val runBy = incidental.flatMap { target.lines.getValue(it) }.distinct().sorted().joinToString(" or ") { report.categoryName(it) }
            "<tr><td>${declarationLink(target, depth)}${fileName(target)}</td><td>${ownerCell(target, depth)}</td>" +
                "<td>${expectationCell(target.expectation)}</td>" +
                "<td class=\"n\"><a href=\"$up${filePage(target.path)}#L${untested.first()}\">${untested.size} of ${tally.assessed}</a></td>" +
                "<td class=\"barcol\">${bar(tally)}</td>" +
                "<td class=\"muted\">${if (incidental.isEmpty()) "" else "${incidental.size} run only by $runBy"}</td></tr>"
        }
        return "<table><thead><tr><th>Declaration</th><th>Construct or module</th><th>Expected</th><th class=\"n\">Untested lines</th><th class=\"barcol\"></th>" +
            "<th>Note</th></tr></thead><tbody>${rows.joinToString("")}</tbody></table>"
    }

    private fun unassessedTable(targets: List<Target>, depth: Int): String {
        val rows = targets.groupBy { it.module }.toSortedMap(compareBy { it.path }).map { (module, moduleTargets) ->
            val tally = moduleTargets.tally()
            listOf(
                code(module.path),
                num(tally.unassessed),
                "${percent(tally.run, tally.unassessed)} run",
                moduleTargets.sortedByDescending { it.lines.size }.take(6).joinToString(", ") { declarationLink(it, depth) },
            )
        }
        return table(listOf("Module", "Lines", "Run by any test", "Declarations"), rows)
    }

    private fun excludedTable(targets: List<Target>): String {
        val rows = targets.groupBy { (if (it.basis == Basis.Module) it.module.path else it.constructs.joinToString()) to (it.expectation as Expectation.None).reason }
            .toSortedMap(compareBy({ it.first }, { it.second }))
            .map { (key, group) -> listOf(code(key.first), esc(key.second), num(group.size), num(group.tally().unassessed)) }
        return "<details><summary>${targets.size} declarations, ${targets.tally().unassessed} lines</summary>" +
            table(listOf("Construct or module", "Reason", "Decls", "Lines"), rows) + "</details>"
    }

    // Listing pages

    private fun featureBody(feature: String, targets: List<Target>): String = buildString {
        append("<h1>Feature <code>${esc(feature)}</code></h1>")
        val assessed = targets.filter { it.assessed }
        append("<p>${bar(assessed.tally())} ${summary(assessed.tally())}</p>")
        assessed.groupBy { layerOf(it) }.toSortedMap().forEach { (layer, layerTargets) ->
            append("<h2>${esc(layer)}</h2>")
            append(declarationTable(layerTargets))
        }
        val other = targets.filter { !it.assessed }
        if (other.isNotEmpty()) {
            append("<h2>Not in the totals</h2>")
            append(declarationTable(other))
        }
    }

    private fun listingBody(title: String, targets: List<Target>, expectation: Expectation?, subtitle: String? = null): String = buildString {
        append("<h1>${esc(title)}</h1>")
        append("<p>Expected: ${expectation?.let { expectationCell(it) } ?: "<span class=\"warn\">None declared</span>"}</p>")
        if (subtitle != null) append("<p class=\"muted\">${esc(subtitle)}</p>")
        val assessed = targets.filter { it.assessed }
        if (assessed.isNotEmpty()) append("<p>${bar(assessed.tally())} ${summary(assessed.tally())}</p>")
        append(declarationTable(targets.sortedWith(compareByDescending<Target> { it.tally.assessed - it.tally.covered }.thenBy { it.path })))
    }

    private fun declarationTable(targets: List<Target>, showPath: Boolean = true): String {
        val rows = targets.map { target ->
            val tally = target.tally
            "<tr${fadedClass(target.assessed)}><td>${declarationLink(target, depth = 1)}${if (showPath) fileName(target) else ""}</td>" +
                "<td>${ownerCell(target, depth = 1)}</td><td>${expectationCell(target.expectation)}</td>" +
                tallyCells(shown(tally)) +
                "</tr>"
        }
        return "<table><thead><tr><th>Declaration</th><th>Construct or module</th><th>Expected</th><th class=\"n\">Lines</th><th class=\"barcol\"></th>" +
            "<th class=\"n\">Covered</th><th class=\"n\">Incidental</th><th class=\"n\">Uncovered</th></tr></thead><tbody>${rows.joinToString("")}</tbody></table>"
    }

    // File pages

    private fun fileBody(path: String, targets: List<Target>): String = buildString {
        val source = root.resolve(path).takeIf { it.isFile }?.readLines().orEmpty()
        val starts = targets.associateBy { it.startLine }
        val owner = mutableMapOf<Int, Target>()
        targets.forEach { target -> target.lines.keys.forEach { owner[it] = target } }

        append("<h1>${esc(path.substringAfterLast('/'))}</h1>")
        append("<p class=\"path\">${esc(path)} · <code>${esc(targets.first().module.path)}</code></p>")
        append("<ul class=\"legend\"><li><i class=\"c\"></i>Covered</li><li><i class=\"i\"></i>Incidental</li><li><i class=\"u\"></i>Uncovered</li><li><i class=\"r\"></i>Run, not in the totals</li><li><i class=\"n\"></i>Not run, not in the totals</li>")
        append("<li><span class=\"chip x\">Unit</span> expected category</li><li><span class=\"chip\">Unit</span> other category</li></ul>")
        append(declarationTable(targets, showPath = false).replace("href=\"../${filePage(path)}#", "href=\"#"))

        append("<table class=\"src\"><tbody>")
        source.forEachIndexed { index, text ->
            val line = index + 1
            starts[line]?.let { target ->
                append("<tr class=\"decl\"><td></td><td></td><td colspan=\"2\" id=\"d$line\">${esc(target.shortName)} · ${ownerCell(target, depth = 1)} · ${expectationCell(target.expectation)}")
                if (target.assessed) append(" · ${summary(target.tally)}")
                append("</td></tr>")
            }
            val lineOwner = owner[line]
            val state = lineOwner?.state(line)
            val chips = lineOwner?.lines?.get(line).orEmpty().sorted().joinToString("") { category ->
                val expected = (lineOwner?.expectation as? Expectation.AnyOf)?.categories?.contains(category) == true
                "<span class=\"chip${if (expected) " x" else ""}\" title=\"${esc(report.categoryName(category))}\">${esc(chip(category))}</span>"
            }
            append("<tr id=\"L$line\"${state?.let { " class=\"${stateClass(it)}\"" } ?: ""}><td class=\"ln\"><a href=\"#L$line\">$line</a></td>")
            append("<td class=\"mark\"></td><td class=\"chips\">$chips</td><td class=\"code\">${esc(text)}</td></tr>")
        }
        append("</tbody></table>")
    }

    // Pieces

    private fun layerOf(target: Target): String = when (target.basis) {
        Basis.Construct -> target.constructs.first().substringBefore('.')
        Basis.Module -> "Module expectation"
        Basis.Unclassified -> "Unclassified"
        Basis.NoExpectation -> "No expectation"
    }

    private fun ownerCell(target: Target, depth: Int): String = when (target.basis) {
        Basis.Construct -> target.constructs.joinToString(", ") { "<a href=\"${"../".repeat(depth)}${constructPage(it)}\">${esc(it)}</a>" }
        Basis.Module -> "<span class=\"muted\">${esc(target.module.path)}</span>"
        Basis.Unclassified -> "<span class=\"warn\">No Construct</span>"
        Basis.NoExpectation -> "<span class=\"warn\">No expectation</span>"
    }

    private fun expectationCell(expectation: Expectation): String = when (expectation) {
        is Expectation.AnyOf -> expectation.categories.joinToString(" ") { "<span class=\"chip x\">${esc(report.categoryName(it))}</span>" }
        is Expectation.None -> "<span class=\"chip none\" title=\"No tests are expected: ${esc(expectation.reason)}\">Not expected</span>"
        Expectation.Unspecified -> "<span class=\"warn\">None declared</span>"
    }

    private fun fileName(target: Target) = "<div class=\"path\" title=\"${esc(target.path)}\">${esc(target.path.substringAfterLast('/'))}</div>"

    private fun declarationLink(target: Target, depth: Int): String {
        val up = if (depth == 0) "" else "../"
        return "<a href=\"$up${filePage(target.path)}#d${target.startLine}\">${esc(target.shortName)}</a>"
    }

    /** A row whose lines are not in the totals is faded, and shows lines a test runs as incidental. */
    private fun fadedClass(assessed: Boolean) = if (assessed) "" else " class=\"faded\""

    private fun shown(tally: Tally) = if (tally.assessed > 0) tally else Tally(incidental = tally.run, uncovered = tally.notRun)

    private fun tallyCells(tally: Tally): String =
        "<td class=\"n\">${tally.assessed}</td><td class=\"barcol\">${bar(tally)}</td>" +
            "<td class=\"n c\">${percent(tally.covered, tally.assessed)}</td><td class=\"n i\">${percent(tally.incidental, tally.assessed)}</td><td class=\"n u\">${percent(tally.uncovered, tally.assessed)}</td>"

    private fun bar(tally: Tally): String {
        val total = tally.assessed + tally.unassessed
        if (total == 0) return ""
        fun seg(count: Int, cls: String) = if (count == 0) "" else "<i class=\"$cls\" style=\"width:${100.0 * count / total}%\"></i>"
        return "<span class=\"bar\" title=\"${summary(tally)}\">${seg(tally.covered, "c")}${seg(tally.incidental, "i")}${seg(tally.uncovered, "u")}${seg(tally.run, "r")}${seg(tally.notRun, "n")}</span>"
    }

    private fun summary(tally: Tally): String = if (tally.assessed > 0) {
        "${percent(tally.covered, tally.assessed)} covered, ${percent(tally.incidental, tally.assessed)} incidental, ${percent(tally.uncovered, tally.assessed)} uncovered of ${tally.assessed} lines"
    } else {
        "${tally.run} of ${tally.unassessed} lines run by a test"
    }

    private fun tile(label: String, count: Int, total: Int, cls: String) =
        "<div class=\"tile $cls\"><div class=\"value\">${percent(count, total)}</div><div class=\"label\">$label · $count lines</div></div>"

    private fun section(title: String, description: String) = "<h2>${esc(title)}</h2><p class=\"muted\">$description</p>"

    private fun table(headers: List<String>, rows: List<List<String>>): String =
        "<table><thead><tr>${headers.joinToString("") { "<th>${esc(it)}</th>" }}</tr></thead><tbody>" +
            rows.joinToString("") { row -> "<tr>${row.joinToString("") { "<td>$it</td>" }}</tr>" } + "</tbody></table>"

    private fun chip(category: String) = report.categoryName(category).take(4)

    private fun stateClass(state: LineState) = when (state) {
        LineState.Covered -> "c"
        LineState.Incidental -> "i"
        LineState.Uncovered -> "u"
        LineState.Run -> "r"
        LineState.NotRun -> "n"
    }

    private fun code(text: String) = "<code>${esc(text)}</code>"
    private fun num(n: Int) = "<span class=\"n\">$n</span>"
    private fun inlineCode(text: String) = esc(text).replace(Regex("`([^`]+)`"), "<code>$1</code>")

    companion object {
        fun constructPage(id: String) = "constructs/$id.html"
        fun featurePage(name: String) = "features/$name.html"
        fun modulePage(path: String) = "modules/${path.trim(':').replace(':', '-')}.html"
        fun filePage(path: String) = "files/${path.replace('/', '~')}.html"

        fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        private val CSS = """
            :root { --bg:#fff; --fg:#1d1f23; --muted:#5f6670; --line:#e3e5e8; --head:#f5f6f7;
              --c:#2f8a46; --i:#d49b00; --u:#c9372c; --r:#9aa3ad; --n:#d7dbe0;
              --c-bg:#e6f4ea; --i-bg:#fdf3d6; --u-bg:#fbe5e2; --r-bg:#f1f3f5; --link:#1f5fbf; }
            @media (prefers-color-scheme: dark) { :root { --bg:#16181b; --fg:#e4e6e9; --muted:#9aa1aa; --line:#2c3036; --head:#1e2125;
              --c:#4cb365; --i:#e2b23a; --u:#e2604f; --r:#6c747e; --n:#3a3f46;
              --c-bg:#183322; --i-bg:#3a3014; --u-bg:#3d1d1a; --r-bg:#22262b; --link:#7fb0ff; } }
            * { box-sizing:border-box; }
            body { margin:0; background:var(--bg); color:var(--fg); font:14px/1.45 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
            main { max-width:1180px; margin:0 auto; padding:24px 16px 64px; }
            a { color:var(--link); text-decoration:none; } a:hover { text-decoration:underline; }
            h1 { font-size:24px; margin:8px 0 4px; } h2 { font-size:17px; margin:36px 0 2px; }
            nav { font-size:13px; } code { font:12.5px ui-monospace, Menlo, monospace; }
            .lede { max-width:760px; } .muted { color:var(--muted); } .warn { color:var(--u); }
            .path { color:var(--muted); font:12px ui-monospace, Menlo, monospace; }
            .tiles { display:flex; gap:12px; margin:16px 0 8px; flex-wrap:wrap; }
            .tile { border:1px solid var(--line); border-left:4px solid; border-radius:6px; padding:10px 16px; min-width:150px; }
            .tile.c { border-left-color:var(--c); } .tile.i { border-left-color:var(--i); } .tile.u { border-left-color:var(--u); }
            .tile .value { font-size:26px; font-weight:600; } .tile .label { color:var(--muted); font-size:12px; }
            table { border-collapse:collapse; width:100%; margin:8px 0; }
            th, td { text-align:left; padding:5px 8px; border-bottom:1px solid var(--line); vertical-align:top; }
            thead th { font-size:12px; color:var(--muted); font-weight:500; }
            tr.group th { background:var(--head); font-weight:600; } tr.group td { background:var(--head); }
            .n { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
            td.c { color:var(--c); } td.i { color:var(--i); } td.u { color:var(--u); }
            .barcol { width:160px; }
            .bar { display:inline-flex; width:150px; height:10px; border-radius:3px; overflow:hidden; background:var(--n); vertical-align:middle; }
            .bar i { display:block; height:100%; }
            .bar .c, .legend .c { background:var(--c); } .bar .i, .legend .i { background:var(--i); } .bar .u, .legend .u { background:var(--u); }
            .bar .r, .legend .r { background:var(--r); } .bar .n, .legend .n { background:var(--n); }
            .chip { display:inline-block; white-space:nowrap; font-size:11px; padding:0 5px; border-radius:3px; border:1px solid var(--muted); color:var(--muted); margin-right:3px; line-height:16px; }
            .chip.x { background:var(--fg); border-color:var(--fg); color:var(--bg); }
            .chip.none { border-style:dashed; cursor:help; }
            tr.faded td { opacity:.4; } tr.faded td:first-child { opacity:.7; }
            .legend { list-style:none; padding:0; display:flex; flex-wrap:wrap; gap:4px 16px; font-size:12px; color:var(--muted); }
            .legend i { display:inline-block; width:10px; height:10px; border-radius:2px; margin-right:5px; vertical-align:-1px; }
            .src { font:12px/1.5 ui-monospace, Menlo, monospace; margin-top:20px; }
            .src td { border:0; padding:0 8px; white-space:pre; }
            .src td.ln { text-align:right; color:var(--muted); user-select:none; width:1%; } .src td.ln a { color:inherit; }
            .src td.mark { width:4px; padding:0; } .src td.chips { width:1%; padding:0 6px; }
            .src tr.c td.mark { background:var(--c); } .src tr.c td.code { background:var(--c-bg); }
            .src tr.i td.mark { background:var(--i); } .src tr.i td.code { background:var(--i-bg); }
            .src tr.u td.mark { background:var(--u); } .src tr.u td.code { background:var(--u-bg); }
            .src tr.r td.mark { background:var(--r); } .src tr.n td.mark { background:var(--n); }
            .src tr.decl td { font:12px -apple-system, sans-serif; padding:14px 8px 4px; white-space:normal; color:var(--muted); border-bottom:1px solid var(--line); }
            .src tr:target td.code { outline:2px solid var(--link); }
            details summary { cursor:pointer; color:var(--muted); }
            ul.problems li { margin:4px 0; }
        """.trimIndent()
    }
}
