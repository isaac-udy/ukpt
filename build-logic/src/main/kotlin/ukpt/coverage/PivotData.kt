package ukpt.coverage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The columns of each row in [pivotData], in order. */
internal val PIVOT_FIELDS = listOf(
    "path", "short", "package", "module", "construct", "basis", "expected", "line", "link",
    "covered", "incidental", "uncovered", "run", "notRun",
)

/**
 * The declarations the index page's pivot table groups, as JSON: `{categories, fields, rows}`, one
 * row per [Target] with the [PIVOT_FIELDS] columns. [packageOf] returns the `package` a source file declares.
 */
internal fun pivotData(report: CoverageReport, packageOf: (String) -> String, linkOf: (Target) -> String): String {
    val rows = report.targets.map { target ->
        val pkg = packageOf(target.path)
        val short = target.declarationName?.let { name -> if (pkg.isNotEmpty() && name.startsWith("$pkg.")) name.removePrefix("$pkg.") else name.substringAfterLast('.') }
            ?: target.shortName
        val expected: JsonElement = when (val expectation = target.expectation) {
            is Expectation.AnyOf -> JsonArray(expectation.categories.map(::JsonPrimitive))
            is Expectation.None -> JsonPrimitive("none")
            Expectation.Unspecified -> JsonPrimitive("unspecified")
        }
        val tally = target.tally
        JsonArray(
            listOf(
                JsonPrimitive(target.path), JsonPrimitive(short), JsonPrimitive(pkg), JsonPrimitive(target.module.path),
                JsonPrimitive(target.constructs.firstOrNull().orEmpty()), JsonPrimitive(target.basis.name.lowercase()), expected,
                JsonPrimitive(target.startLine), JsonPrimitive(linkOf(target)),
                JsonPrimitive(tally.covered), JsonPrimitive(tally.incidental), JsonPrimitive(tally.uncovered),
                JsonPrimitive(tally.run), JsonPrimitive(tally.notRun),
            ),
        )
    }
    val categories = JsonObject(report.categoryNames.mapValues { JsonPrimitive(it.value) })
    val json = JsonObject(mapOf("categories" to categories, "fields" to JsonArray(PIVOT_FIELDS.map(::JsonPrimitive)), "rows" to JsonArray(rows)))
    // The JSON sits inside a <script> element, which a literal `</` would close.
    return json.toString().replace("</", "<\\/")
}

private val PACKAGE_DECLARATION = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

/** The `package` a Kotlin source declares, or an empty string for the default package. */
internal fun declaredPackage(source: String): String = PACKAGE_DECLARATION.find(source)?.groupValues?.get(1).orEmpty()
