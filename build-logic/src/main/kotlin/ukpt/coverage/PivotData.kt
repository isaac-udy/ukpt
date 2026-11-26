package ukpt.coverage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val DESCRIPTION_FIELDS = listOf(
    "path", "short", "package", "module", "area", "feature", "affinity", "construct", "layer", "basis", "expected", "line", "link",
)

/** The columns of each row in [pivotData], in order. */
internal val PIVOT_FIELDS = DESCRIPTION_FIELDS + listOf("covered", "incidental", "uncovered", "run", "notRun")

/** The columns of each row in [mutationPivotData], in order. */
internal val MUTATION_PIVOT_FIELDS = DESCRIPTION_FIELDS + listOf("killed", "survived", "uncovered")

/**
 * The declarations the index page's coverage table groups, as JSON: `{categories, fields, rows}`, one
 * row per [Target] with the [PIVOT_FIELDS] columns. [packageOf] returns the `package` a source file declares.
 */
internal fun pivotData(report: CoverageReport, packageOf: (String) -> String, linkOf: (Target) -> String): String {
    val rows = report.targets.map { target ->
        val tally = target.tally
        description(target, packageOf, linkOf) +
            listOf(tally.covered, tally.incidental, tally.uncovered, tally.run, tally.notRun).map(::JsonPrimitive)
    }
    return encode(report, PIVOT_FIELDS, rows)
}

/** The declarations that contain mutants, for the mutation table: one row per [Target] with the [MUTATION_PIVOT_FIELDS] columns. */
internal fun mutationPivotData(report: CoverageReport, packageOf: (String) -> String, linkOf: (Target) -> String): String {
    val rows = report.targets.mapNotNull { target ->
        val mutants = report.mutantsByTarget[target]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val tally = MutantTally.of(mutants)
        description(target, packageOf, linkOf) + listOf(tally.killed, tally.survived, tally.uncovered).map(::JsonPrimitive)
    }
    return encode(report, MUTATION_PIVOT_FIELDS, rows)
}

private fun description(target: Target, packageOf: (String) -> String, linkOf: (Target) -> String): List<JsonElement> {
    val pkg = packageOf(target.path)
    val short = target.declarationName?.let { name -> if (pkg.isNotEmpty() && name.startsWith("$pkg.")) name.removePrefix("$pkg.") else name.substringAfterLast('.') }
        ?: target.shortName
    val expected: JsonElement = when (val expectation = target.expectation) {
        is Expectation.AnyOf -> JsonArray(expectation.categories.map(::JsonPrimitive))
        is Expectation.None -> JsonPrimitive("none")
        Expectation.Unspecified -> JsonPrimitive("unspecified")
    }
    return listOf(
        JsonPrimitive(target.path), JsonPrimitive(short), JsonPrimitive(pkg), JsonPrimitive(target.module.path),
        JsonPrimitive(target.module.area), JsonPrimitive(target.module.featurePath), JsonPrimitive(target.module.affinity),
        JsonPrimitive(target.constructs.firstOrNull().orEmpty()), JsonPrimitive(layerOf(target)), JsonPrimitive(target.basis.name.lowercase()), expected,
        JsonPrimitive(target.startLine), JsonPrimitive(linkOf(target)),
    )
}

private fun encode(report: CoverageReport, fields: List<String>, rows: List<List<JsonElement>>): String {
    val categories = JsonObject(report.categoryNames.mapValues { JsonPrimitive(it.value) })
    val json = JsonObject(mapOf("categories" to categories, "fields" to JsonArray(fields.map(::JsonPrimitive)), "rows" to JsonArray(rows.map(::JsonArray))))
    // The JSON sits inside a <script> element, which a literal `</` would close.
    return json.toString().replace("</", "<\\/")
}

private fun layerOf(target: Target): String = target.constructs.firstOrNull()?.substringBefore('.') ?: when (target.basis) {
    Basis.Module -> "Module expectation"
    Basis.NoExpectation -> "No expectation"
    else -> target.group ?: "Unclassified"
}

private val PACKAGE_DECLARATION = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

/** The `package` a Kotlin source declares, or an empty string for the default package. */
internal fun declaredPackage(source: String): String = PACKAGE_DECLARATION.find(source)?.groupValues?.get(1).orEmpty()
