package ukpt.coverage

import java.io.File

/**
 * The HTML report: `index.html`, whose table groups every declaration by module or by Construct,
 * and one page per source file with each measured line marked.
 */
class HtmlReport(private val report: CoverageReport, private val root: File, private val outputDir: File) {

    private val targetsByPath = report.targets.groupBy { it.path }
    private val packages = mutableMapOf<String, String>()

    fun write() {
        outputDir.resolve("report.css").writeText(CSS + "\n" + resource("pivot.css"))
        outputDir.resolve("pivot.js").writeText(resource("pivot.js"))
        write("index.html", "Test coverage", depth = 0, indexBody())
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
            |</main>${if (depth == 0) "<script src=\"pivot.js\"></script>" else ""}</body></html>
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
        append("<div class=\"pivot\">")
        append("<div class=\"tiles\" id=\"tiles\">")
        append(tile("Covered", total.covered, total.assessed, "c"))
        append(tile("Incidental", total.incidental, total.assessed, "i"))
        append(tile("Uncovered", total.uncovered, total.assessed, "u"))
        append("</div>")
        append(PIVOT_CONTROLS)
        append("<noscript><p class=\"warn\">The coverage table requires JavaScript. Each source file's page under <code>files/</code> works without it.</p></noscript>")
        append("<script type=\"application/json\" id=\"coverage-data\">")
        append(pivotData(report, ::packageOf) { filePage(it.path) + "#d" + it.startLine })
        append("</script>")
        append("</div>")

        report.mutations?.let { append(mutantSection(it)) }

        val gaps = assessed.filter { it.tally.covered < it.tally.assessed }.sortedByDescending { it.tally.assessed - it.tally.covered }.take(20)
        append(collapsed("Gaps", "Declarations with the most lines that no expected test runs, most first. Untested lines opens the first such line.", gapTable(gaps, depth = 0)))
        if (report.notMeasured.isNotEmpty() || report.generatedFiles.isNotEmpty() || report.objectHeaderLines > 0) {
            val body = buildString {
                append(table(listOf("Module", "Source set", "Files"), report.notMeasured.map { listOf(code(it.module), code(it.sourceSet), num(it.files)) }))
                if (report.objectHeaderLines > 0) {
                    append("<p class=\"muted\">${report.objectHeaderLines} lines that open an <code>object</code> body are left out: Kover attributes the object's creation to that line, and only code that reads the object itself creates the object.</p>")
                }
                if (report.generatedFiles.isNotEmpty()) {
                    append("<details><summary>${report.generatedFiles.size} generated source files are left out</summary><ul>${report.generatedFiles.joinToString("") { "<li>${code(it)}</li>" }}</ul></details>")
                }
            }
            append(collapsed("Not measured", "Kover measures JVM and Android host tests only. These source sets compile for other hosts and have no coverage figures.", body))
        }
        if (report.problems.isNotEmpty()) {
            append(collapsed("Report problems (${report.problems.size})", "Coverage data the report could not attribute.", "<ul class=\"problems\">${report.problems.joinToString("") { "<li>${inlineCode(it)}</li>" }}</ul>"))
        }
    }

    private fun mutantSection(results: MutationResults): String {
        val tally = MutantTally.of(results.mutants)
        val targetOf = report.mutantsByTarget.flatMap { (target, mutants) -> mutants.map { it to target } }.toMap()
        val gaps = results.mutants.filter { !it.status.detected }
            .sortedWith(compareBy({ it.status != MutantStatus.Survived }, { it.path }, { it.line }))
        val description = "${tally.total} mutants from the last <code>./gradlew runMutants</code> on ${results.hosts.joinToString()}: " +
            "${tally.killed} killed, ${tally.survived} survived, ${tally.uncovered} uncovered. A mutant counts as killed only by a test " +
            "from a category its Construct expects." +
            if (results.stale > 0) " ${results.stale} mutants are left out because their line changed after the run; run <code>./gradlew runMutants</code> again." else ""
        val body = if (gaps.isEmpty()) {
            "<p class=\"muted\">Every mutant is killed.</p>"
        } else {
            val rows = gaps.joinToString("") { mutant ->
                val target = targetOf[mutant]
                val name = target?.let { esc(it.shortName) } ?: esc(mutant.path.substringAfterLast('/'))
                "<tr><td><a href=\"${filePage(mutant.path)}#L${mutant.line}\">$name</a><div class=\"path\" title=\"${esc(mutant.path)}\">" +
                    "${esc(mutant.path.substringAfterLast('/'))}:${mutant.line}</div></td>" +
                    "<td>${target?.let { ownerCell(it) }.orEmpty()}</td><td>${target?.let { expectationCell(it.expectation) }.orEmpty()}</td>" +
                    "<td>${mutationCell(mutant)}</td><td>${statusChip(mutant)}</td>" +
                    "<td class=\"n\">${if (mutant.status == MutantStatus.Survived) mutant.candidateTests else ""}</td></tr>"
            }
            "<table><thead><tr><th>Declaration</th><th>Construct or module</th><th>Expected</th><th>Mutation</th><th>Status</th>" +
                "<th class=\"n\" title=\"Tests from an expected category that ran the mutant\">Tests</th></tr></thead><tbody>$rows</tbody></table>"
        }
        return collapsed("Mutants not killed (${gaps.size})", description, body)
    }

    private fun mutationCell(mutant: Mutant) =
        "<span class=\"muted\">${esc(OPERATOR_LABELS[mutant.operator] ?: mutant.operator)}</span> ${code(mutant.text)}"

    private fun statusChip(mutant: Mutant): String {
        val title = when (mutant.status) {
            MutantStatus.Survived -> "${mutant.candidateTests} expected tests ran the mutant and passed"
            MutantStatus.Uncovered -> "No test from an expected category runs the mutant"
            else -> listOfNotNull(mutant.killedBy, mutant.failure).joinToString(": ")
        }
        return "<span class=\"mchip ${mutant.status.id}\" title=\"${esc(title)}\">${mutant.status.label}</span>"
    }

    private fun packageOf(path: String): String = packages.getOrPut(path) {
        root.resolve(path).takeIf { it.isFile }?.let { declaredPackage(it.readText()) }.orEmpty()
    }

    private fun gapTable(targets: List<Target>, depth: Int): String {
        if (targets.isEmpty()) return "<p class=\"muted\">Every line expected to be tested is covered.</p>"
        val up = "../".repeat(depth)
        val rows = targets.map { target ->
            val tally = target.tally
            val untested = (target.linesIn(LineState.Uncovered) + target.linesIn(LineState.Incidental)).sorted()
            val incidental = target.linesIn(LineState.Incidental)
            val runBy = incidental.flatMap { target.lines.getValue(it) }.distinct().sorted().joinToString(" or ") { report.categoryName(it) }
            "<tr><td>${declarationLink(target, depth)}${fileName(target)}</td><td>${ownerCell(target)}</td>" +
                "<td>${expectationCell(target.expectation)}</td>" +
                "<td class=\"n\"><a href=\"$up${filePage(target.path)}#L${untested.first()}\">${untested.size} of ${tally.assessed}</a></td>" +
                "<td class=\"barcol\">${bar(tally)}</td>" +
                "<td class=\"muted\">${if (incidental.isEmpty()) "" else "${incidental.size} run only by $runBy"}</td></tr>"
        }
        return "<table><thead><tr><th>Declaration</th><th>Construct or module</th><th>Expected</th><th class=\"n\">Untested lines</th><th class=\"barcol\"></th>" +
            "<th>Note</th></tr></thead><tbody>${rows.joinToString("")}</tbody></table>"
    }

    private fun declarationTable(targets: List<Target>, showPath: Boolean = true): String {
        val rows = targets.map { target ->
            val tally = target.tally
            "<tr${fadedClass(target.assessed)}><td>${declarationLink(target, depth = 1)}${if (showPath) fileName(target) else ""}</td>" +
                "<td>${ownerCell(target)}</td><td>${expectationCell(target.expectation)}</td>" +
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
        append("<li><span class=\"chip x\">Unit</span> expected category</li><li><span class=\"chip\">Unit</span> other category</li>")
        if (report.mutations != null) {
            append("<li><span class=\"mchip killed\">K</span> mutant killed</li><li><span class=\"mchip survived\">S</span> survived</li><li><span class=\"mchip timed-out\">T</span> timed out</li><li><span class=\"mchip uncovered\">U</span> uncovered</li>")
        }
        append("</ul>")
        append(declarationTable(targets, showPath = false).replace("href=\"../${filePage(path)}#", "href=\"#"))

        append("<table class=\"src\"><tbody>")
        source.forEachIndexed { index, text ->
            val line = index + 1
            starts[line]?.let { target ->
                append("<tr class=\"decl\"><td></td><td></td><td colspan=\"2\" id=\"d$line\">${esc(target.shortName)} · ${ownerCell(target)} · ${expectationCell(target.expectation)}")
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
            val mutants = report.mutations?.onLine(path, line).orEmpty().joinToString("") { mutant ->
                val detail = listOfNotNull(
                    "${OPERATOR_LABELS[mutant.operator] ?: mutant.operator}: ${mutant.text}",
                    mutant.status.label,
                    mutant.killedBy?.let { "by $it" },
                    mutant.failure,
                    "id ${mutant.id}",
                ).joinToString(" · ")
                "<span class=\"mchip ${mutant.status.id}\" title=\"${esc(detail)}\">${mutant.status.label.first()}</span>"
            }
            append("<td class=\"mark\"></td><td class=\"chips\">$chips$mutants</td><td class=\"code\">${esc(text)}</td></tr>")
        }
        append("</tbody></table>")
    }

    // Pieces

    private fun ownerCell(target: Target): String = when (target.basis) {
        Basis.Construct -> target.constructs.joinToString(", ") { code(it) }
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

    private fun collapsed(title: String, description: String, body: String) =
        "<details class=\"section\"><summary><h2>${esc(title)}</h2><span class=\"muted\">$description</span></summary>$body</details>"

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
        private val OPERATOR_LABELS = mapOf("negate-condition" to "negated", "negate-boolean-return" to "negated return")

        fun filePage(path: String) = "files/${path.replace('/', '~')}.html"

        private fun resource(name: String): String =
            requireNotNull(HtmlReport::class.java.getResource("/ukpt/coverage/$name")) { "The build-logic jar contains no ukpt/coverage/$name resource." }.readText()

        fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        /** The index page's table and its controls; `pivot.js` fills them from `#coverage-data`. */
        private val PIVOT_CONTROLS = """
            <div class="controls">
              <div class="axes">
                <div class="seg" role="group" aria-label="Group by" id="views">
                  <button data-view="modules" aria-pressed="true">By module</button>
                  <button data-view="constructs" aria-pressed="false">By Construct</button>
                </div>
                <div class="seg sub" role="group" aria-label="Then group by" id="inner"></div>
                <div class="seg opts" role="group" aria-label="Grouping options" id="axisOpts"></div>
              </div>
              <div class="right">
              <div class="expanders">
                <span class="lbl">Expand</span><button class="link" id="expandOne">one</button><button class="link" id="expandAll">all</button>
                <span class="lbl">Collapse</span><button class="link" id="collapseOne">one</button><button class="link" id="collapseAll">all</button>
              </div>
              <div class="tools">
                <div class="searchwrap" id="searchWrap">
                  <button class="iconbtn" id="searchBtn" aria-label="Search declarations" aria-expanded="false">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><line x1="20" y1="20" x2="16.2" y2="16.2"/></svg>
                  </button>
                  <input type="search" id="search" placeholder="Search names, packages, files…" aria-label="Search declarations" tabindex="-1">
                </div>
                <details class="menu" id="filterMenu">
                  <summary class="iconbtn" aria-label="Filter">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><polygon points="21 4 3 4 10 12.5 10 19 14 21 14 12.5 21 4"/></svg>
                    <span class="badge" id="filterCount" hidden></span>
                  </summary>
                  <div class="panel filters" id="facets"></div>
                </details>
                <details class="menu" id="displayMenu">
                  <summary class="iconbtn" aria-label="Display options">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
                  </summary>
                  <div class="panel display">
                    <label><input type="checkbox" id="optCollapseFiles" checked><span>Collapse single-declaration files<span class="hint">A file with one declaration shows that declaration in place of a file row.</span></span></label>
                    <label><input type="checkbox" id="optQualified" checked><span>Sort names by package<span class="hint">Sort declarations by package, then name, so declarations from one package stay together.</span></span></label>
                    <label><input type="checkbox" id="optShortPkg"><span>Short package names<span class="hint">Name each package relative to the package row above it. The first package row always shows the full name.</span></span></label>
                  </div>
                </details>
              </div>
              </div>
            </div>
            <div class="active" id="active"></div>

            <div class="tablewrap">
              <table>
                <thead><tr id="heads"><th data-sort="name">Name</th><th>Expected</th><th class="n" data-sort="decls">Decls</th><th class="n" data-sort="lines">Lines</th><th class="barcol"><details class="menu key" id="keyMenu"><summary class="keybtn" aria-label="Colour key" title="Colour key">?</summary><div class="panel keypanel"><ul class="legend vertical">
                  <li><i class="c"></i>Covered: an expected test category runs the line</li>
                  <li><i class="i"></i>Incidental: only other categories run the line</li>
                  <li><i class="u"></i>Uncovered</li>
                  <li><i class="r"></i>Ran, not expected to be tested</li>
                  <li><i class="n"></i>Not run, not expected to be tested</li>
                </ul></div></details></th><th class="n" data-sort="covered">Covered</th><th class="n" data-sort="incidental">Incidental</th><th class="n" data-sort="uncovered">Uncovered</th><th class="n" data-sort="mutants" id="mutantsHead" title="Mutants killed of all mutants; S survived, U uncovered" hidden>Mutants</th></tr></thead>
                <tbody id="body"></tbody>
              </table>
            </div>
            <div class="foot">
              <span class="status" id="status"></span>
            </div>
        """.trimIndent()

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
            .mchip { display:inline-block; white-space:nowrap; font-size:11px; padding:0 5px; border-radius:3px; line-height:16px; margin-right:3px; color:#fff; cursor:help; }
            .mchip.killed, .mchip.timed-out { background:var(--c); } .mchip.survived { background:var(--u); } .mchip.uncovered { background:var(--i); color:#1d1f23; }
            td.mut { white-space:nowrap; } td.mut .ms { color:var(--u); } td.mut .mu { color:var(--i); }
        """.trimIndent()
    }
}
