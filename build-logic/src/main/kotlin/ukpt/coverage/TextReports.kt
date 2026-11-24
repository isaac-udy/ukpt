package ukpt.coverage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** `index.md`: the summary tables and the gaps, with `path:line` references for agents and pull requests. */
fun markdownReport(report: CoverageReport): String = buildString {
    val assessed = report.targets.filter { it.assessed }
    val total = assessed.tally()
    appendLine("# Test coverage")
    appendLine()
    appendLine("**${percent(total.covered, total.assessed)} covered**, ${percent(total.incidental, total.assessed)} incidental, ${percent(total.uncovered, total.assessed)} uncovered, of ${total.assessed} lines expected to be tested.")
    appendLine()
    appendLine("A line is covered when a test from a category its Construct expects runs it, and incidental when only tests from other categories run it. End-to-end Tests are not measured. `index.html` has the per-file view.")
    appendLine()
    appendLine("## By Construct")
    appendLine()
    appendLine("| Construct | Expected | Decls | Lines | Covered | Incidental | Uncovered |")
    appendLine("|---|---|--:|--:|--:|--:|--:|")
    val byConstruct = assessed.filter { it.basis == Basis.Construct && it.module.feature != null }
        .flatMap { target -> target.constructs.map { it to target } }.groupBy({ it.first }, { it.second })
    val order = report.classification.constructs.keys.toList()
    byConstruct.keys.sortedBy { order.indexOf(it) }.forEach { construct ->
        val targets = byConstruct.getValue(construct)
        val tally = targets.tally()
        appendLine("| `$construct` | ${report.describe(report.classification.constructs[construct]?.expectation ?: Expectation.Unspecified)} | ${targets.size} | ${tallyColumns(tally)} |")
    }
    appendLine()
    appendLine("## By feature")
    appendLine()
    appendLine("| Feature | Layer | Lines | Covered | Incidental | Uncovered |")
    appendLine("|---|---|--:|--:|--:|--:|")
    assessed.filter { it.module.feature != null }.groupBy { it.module.feature!! }.toSortedMap().forEach { (feature, targets) ->
        targets.groupBy { it.constructs.firstOrNull()?.substringBefore('.') ?: "Module expectation" }.toSortedMap().forEach { (layer, layerTargets) ->
            appendLine("| `$feature` | $layer | ${tallyColumns(layerTargets.tally())} |")
        }
    }
    appendLine()
    appendLine("## Modules")
    appendLine()
    appendLine("| Module | Expected | Lines | Covered | Incidental | Uncovered |")
    appendLine("|---|---|--:|--:|--:|--:|")
    report.targets.filter { it.module.feature == null }.groupBy { it.module }.toSortedMap(compareBy { it.path }).forEach { (module, targets) ->
        val moduleAssessed = targets.filter { it.assessed }
        val expected = module.expectation?.let(report::describe) ?: "**None declared**"
        if (moduleAssessed.isEmpty()) {
            appendLine("| `${module.path}` | $expected | ${targets.tally().unassessed} | – | – | – |")
        } else {
            appendLine("| `${module.path}` | $expected | ${tallyColumns(moduleAssessed.tally())} |")
        }
    }
    appendLine()
    appendLine("## Gaps")
    appendLine()
    val gaps = assessed.filter { it.tally.covered < it.tally.assessed }.sortedByDescending { it.tally.assessed - it.tally.covered }.take(20)
    if (gaps.isEmpty()) appendLine("Every line expected to be tested is covered.")
    gaps.forEach { target ->
        val categories = (target.expectation as Expectation.AnyOf).categories.map(report::categoryName)
        val untested = (target.linesIn(LineState.Uncovered) + target.linesIn(LineState.Incidental)).sorted()
        val incidental = target.linesIn(LineState.Incidental)
        append("- `${target.path}:${untested.first()}` `${target.shortName}`: ${untested.size} of ${target.tally.assessed} lines untested (${lineRanges(untested)}); expects ${categories.joinToString(" or ")}")
        if (incidental.isNotEmpty()) {
            val by = incidental.flatMap { target.lines.getValue(it) }.distinct().sorted().joinToString(" or ") { report.categoryName(it) }
            append("; ${incidental.size} run only by $by")
        }
        appendLine()
    }
    val unclassified = report.targets.filter { it.basis == Basis.Unclassified || it.basis == Basis.NoExpectation }
    if (unclassified.isNotEmpty()) {
        appendLine()
        appendLine("## Unclassified and no expectation")
        appendLine()
        unclassified.groupBy { it.module.path }.toSortedMap().forEach { (module, targets) ->
            appendLine("- `$module`: ${targets.tally().unassessed} lines in ${targets.joinToString(", ") { "`${it.shortName}`" }}")
        }
    }
    if (report.problems.isNotEmpty()) {
        appendLine()
        appendLine("## Report problems")
        appendLine()
        report.problems.forEach { appendLine("- $it") }
    }
}

private fun tallyColumns(tally: Tally) =
    "${tally.assessed} | ${percent(tally.covered, tally.assessed)} | ${percent(tally.incidental, tally.assessed)} | ${percent(tally.uncovered, tally.assessed)}"

/** `coverage.json`: every target with its expectation and its lines by state. */
fun jsonReport(report: CoverageReport): String {
    fun expectation(e: Expectation): JsonElement = when (e) {
        is Expectation.AnyOf -> JsonObject(mapOf("kind" to JsonPrimitive("anyOf"), "categories" to JsonArray(e.categories.map(::JsonPrimitive))))
        is Expectation.None -> JsonObject(mapOf("kind" to JsonPrimitive("none"), "reason" to JsonPrimitive(e.reason)))
        Expectation.Unspecified -> JsonObject(mapOf("kind" to JsonPrimitive("unspecified")))
    }
    fun tally(t: Tally) = JsonObject(
        mapOf(
            "covered" to JsonPrimitive(t.covered), "incidental" to JsonPrimitive(t.incidental), "uncovered" to JsonPrimitive(t.uncovered),
            "run" to JsonPrimitive(t.run), "notRun" to JsonPrimitive(t.notRun),
        ),
    )
    val targets = report.targets.map { target ->
        JsonObject(
            mapOf(
                "path" to JsonPrimitive(target.path),
                "startLine" to JsonPrimitive(target.startLine),
                "endLine" to JsonPrimitive(target.endLine),
                "name" to JsonPrimitive(target.name),
                "module" to JsonPrimitive(target.module.path),
                "constructs" to JsonArray(target.constructs.map(::JsonPrimitive)),
                "basis" to JsonPrimitive(target.basis.name.replaceFirstChar(Char::lowercase)),
                "expected" to expectation(target.expectation),
                "tally" to tally(target.tally),
                "lines" to JsonObject(target.lines.mapKeys { it.key.toString() }.mapValues { (line, categories) ->
                    JsonObject(mapOf("state" to JsonPrimitive(target.state(line.toInt()).name.replaceFirstChar(Char::lowercase)), "categories" to JsonArray(categories.sorted().map(::JsonPrimitive))))
                }),
            ),
        )
    }
    val root = JsonObject(
        mapOf(
            "summary" to tally(report.targets.filter { it.assessed }.tally()),
            "categories" to JsonObject(report.categoryNames.mapValues { JsonPrimitive(it.value) }),
            "targets" to JsonArray(targets),
            "problems" to JsonArray(report.problems.map(::JsonPrimitive)),
        ),
    )
    return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
}

/** The lines `testCoverageReport` prints. */
fun consoleSummary(report: CoverageReport): String = buildString {
    val assessed = report.targets.filter { it.assessed }
    val total = assessed.tally()
    appendLine("Test coverage: ${percent(total.covered, total.assessed)} covered, ${percent(total.incidental, total.assessed)} incidental, ${percent(total.uncovered, total.assessed)} uncovered, of ${total.assessed} lines expected to be tested")
    assessed.groupBy { target ->
        target.module.feature?.let { "feature $it" } ?: target.module.path
    }.toSortedMap().forEach { (scope, targets) ->
        val tally = targets.tally()
        appendLine("  ${scope.padEnd(34)} ${percent(tally.covered, tally.assessed).padStart(4)} covered  ${percent(tally.incidental, tally.assessed).padStart(4)} incidental  (${tally.assessed} lines)")
    }
    val noExpectation = report.modules.filter { it.feature == null && it.expectation == null && report.targets.any { t -> t.module == it } }
    if (noExpectation.isNotEmpty()) appendLine("  No module expectation: ${noExpectation.joinToString { it.path }}. Declare one in the root testCoverage { } block.")
    if (report.problems.isNotEmpty()) appendLine("  ${report.problems.size} report problems; see the report.")
}
