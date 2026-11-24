package ukpt.coverage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The test categories a declaration is expected to be covered by. */
sealed interface Expectation {
    data class AnyOf(val categories: List<String>) : Expectation
    data class None(val reason: String) : Expectation
    data object Unspecified : Expectation

    companion object {
        /** Reads the `anyOf:unit,adapter` and `none:reason` encoding that the `testCoverage { }` extension stores. */
        fun decode(encoded: String): Expectation = when {
            encoded.startsWith("anyOf:") -> AnyOf(encoded.removePrefix("anyOf:").split(','))
            encoded.startsWith("none:") -> None(encoded.removePrefix("none:"))
            else -> error("Unknown test expectation encoding `$encoded`")
        }
    }
}

/** Where a declaration's expectation is declared. */
enum class Basis {
    /** The Construct the declaration matches. */
    Construct,

    /** The module expectation in the root `testCoverage { }` block; applies to lines no Construct claims. */
    Module,

    /** A feature module declaration that matches no Construct. */
    Unclassified,

    /** A module outside `:feature` with no module expectation, or a Construct with no `testedBy`. */
    NoExpectation,
}

data class ConstructInfo(val id: String, val expectation: Expectation) {
    val group: String get() = id.substringBefore('.')
}

/** [group] is the id of the rule group the architecture classifier places the declaration in, or null. */
data class Declaration(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val name: String,
    val constructs: List<String>,
    val group: String? = null,
)

class Classification(
    val categoryNames: Map<String, String>,
    val constructs: Map<String, ConstructInfo>,
    val declarations: List<Declaration>,
) {
    companion object {
        fun parse(json: String): Classification {
            val root = Json.parseToJsonElement(json).jsonObject
            val categories = root.getValue("categories").jsonArray.associate {
                it.jsonObject.string("id") to it.jsonObject.string("name")
            }
            val constructs = root.getValue("constructs").jsonArray.map { element ->
                val construct = element.jsonObject
                val testedBy = construct.getValue("testedBy").jsonObject
                val expectation = when (testedBy.string("kind")) {
                    "anyOf" -> Expectation.AnyOf(testedBy.getValue("categories").jsonArray.map { it.jsonPrimitive.content })
                    "none" -> Expectation.None(testedBy.string("reason"))
                    else -> Expectation.Unspecified
                }
                ConstructInfo(construct.string("id"), expectation)
            }
            val declarations = root.getValue("declarations").jsonArray.map { element ->
                val declaration = element.jsonObject
                Declaration(
                    path = declaration.string("path"),
                    startLine = declaration.getValue("startLine").jsonPrimitive.int,
                    endLine = declaration.getValue("endLine").jsonPrimitive.int,
                    name = declaration.string("name"),
                    constructs = (declaration["constructs"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content },
                    group = (declaration["group"] as? JsonPrimitive)?.contentOrNull,
                )
            }
            return Classification(categories, constructs.associateBy { it.id }, declarations)
        }

        private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    }
}

/** A Gradle module in the report. [directory] is relative to the repository root, without a trailing slash. */
data class ModuleInfo(val path: String, val directory: String, val expectation: Expectation?) {
    private val segments: List<String> get() = path.split(':').filter(String::isNotEmpty)

    val feature: String? get() = segments.takeIf { it.size >= 2 && it[0] == "feature" }?.get(1)

    /** `:feature:<group or feature>`, `:app` or `:platform`: the report's top-level grouping. */
    val area: String get() = if (feature != null) ":feature:$feature" else ":${segments.firstOrNull().orEmpty()}"

    /**
     * The module path without its side: `:feature:gate:auth` for `:feature:gate:auth:client`, and
     * `:app:client` for `:app:client:android`. A `:platform` module is its own feature path.
     */
    val featurePath: String get() = when {
        feature != null && segments.size >= 3 -> ":" + segments.dropLast(1).joinToString(":")
        segments.firstOrNull() == "app" && segments.size >= 2 -> ":app:${segments[1]}"
        else -> path
    }

    /** The side a module belongs to: the last segment of a feature or app module, the second of a platform module. */
    val affinity: String get() = when {
        feature != null || segments.firstOrNull() == "app" -> segments.last()
        else -> segments.getOrElse(1) { segments.last() }
    }
}

enum class LineState { Covered, Incidental, Uncovered, Run, NotRun }

/**
 * Measured lines that one owner accounts for: a classified declaration, or the lines of one file
 * that no declaration's range contains.
 */
class Target(
    val path: String,
    val module: ModuleInfo,
    /** The declaration's qualified name; null for the lines of [path] that no declaration's range contains. */
    val declarationName: String?,
    val startLine: Int,
    val endLine: Int,
    val constructs: List<String>,
    val expectation: Expectation,
    val basis: Basis,
    /** Measured line → the categories whose tests ran the line. */
    val lines: Map<Int, Set<String>>,
    /** The rule group the architecture classifier places the declaration in; null for lines outside every declaration. */
    val group: String? = null,
) {
    val assessed: Boolean get() = expectation is Expectation.AnyOf
    val name: String get() = declarationName ?: path.substringAfterLast('/')
    val shortName: String get() = declarationName?.substringAfterLast('.') ?: "${path.substringAfterLast('/')} (outside declarations)"

    fun state(line: Int): LineState {
        val hits = lines.getValue(line)
        val expected = (expectation as? Expectation.AnyOf)?.categories
        return when {
            expected == null -> if (hits.isEmpty()) LineState.NotRun else LineState.Run
            hits.any { it in expected } -> LineState.Covered
            hits.isNotEmpty() -> LineState.Incidental
            else -> LineState.Uncovered
        }
    }

    fun linesIn(state: LineState): List<Int> = lines.keys.filter { state(it) == state }.sorted()

    val tally: Tally by lazy { Tally.of(lines.keys.map(::state)) }
}

data class Tally(val covered: Int = 0, val incidental: Int = 0, val uncovered: Int = 0, val run: Int = 0, val notRun: Int = 0) {
    val assessed: Int get() = covered + incidental + uncovered
    val unassessed: Int get() = run + notRun

    operator fun plus(other: Tally) = Tally(
        covered + other.covered,
        incidental + other.incidental,
        uncovered + other.uncovered,
        run + other.run,
        notRun + other.notRun,
    )

    companion object {
        fun of(states: List<LineState>) = Tally(
            covered = states.count { it == LineState.Covered },
            incidental = states.count { it == LineState.Incidental },
            uncovered = states.count { it == LineState.Uncovered },
            run = states.count { it == LineState.Run },
            notRun = states.count { it == LineState.NotRun },
        )
    }
}

fun Iterable<Target>.tally(): Tally = fold(Tally()) { sum, target -> sum + target.tally }

/** Production source files in a module's source set that no measured test host compiles. */
data class NotMeasured(val module: String, val sourceSet: String, val files: Int)

class CoverageReport(
    val targets: List<Target>,
    val classification: Classification,
    val modules: List<ModuleInfo>,
    val categoryNames: Map<String, String>,
    val notMeasured: List<NotMeasured>,
    /** Generated source files the tests ran; the report leaves them out. */
    val generatedFiles: List<String>,
    /** Lines that open an `object` body, which the report leaves out. */
    val objectHeaderLines: Int,
    val problems: List<String>,
) {
    fun categoryName(id: String): String = categoryNames[id] ?: id

    fun describe(expectation: Expectation): String = when (expectation) {
        is Expectation.AnyOf -> expectation.categories.joinToString(" or ") { categoryName(it) }
        is Expectation.None -> "No tests: ${expectation.reason}"
        Expectation.Unspecified -> "No expectation"
    }
}

/**
 * Assigns every measured line to a [Target]. A line inside a declaration's range belongs to the
 * innermost such declaration; the declaration's Constructs set the expectation, and the module
 * expectation applies only when the declaration matches no Construct.
 */
fun buildTargets(
    classification: Classification,
    modules: List<ModuleInfo>,
    hits: Map<String, Map<Int, Set<String>>>,
    problems: MutableList<String>,
): List<Target> {
    val declarationsByPath = classification.declarations.groupBy { it.path }
    val modulesByDepth = modules.sortedByDescending { it.directory.length }
    val targets = mutableListOf<Target>()
    for ((path, lines) in hits.toSortedMap()) {
        val module = modulesByDepth.firstOrNull { path.startsWith(it.directory + "/") }
        if (module == null) {
            problems += "`$path` is in no reported module; ${lines.size} measured lines are left out."
            continue
        }
        val declarations = declarationsByPath[path].orEmpty()
        val owned = lines.entries.groupBy { (line, _) ->
            declarations.filter { line in it.startLine..it.endLine }.minByOrNull { it.endLine - it.startLine }
        }
        for ((declaration, entries) in owned) {
            val constructs = declaration?.constructs.orEmpty()
            val (expectation, basis) = resolveExpectation(constructs, module, classification)
            targets += Target(
                path = path,
                module = module,
                declarationName = declaration?.name,
                startLine = declaration?.startLine ?: entries.minOf { it.key },
                endLine = declaration?.endLine ?: entries.maxOf { it.key },
                constructs = constructs,
                expectation = expectation,
                basis = basis,
                lines = entries.associate { it.key to it.value }.toSortedMap(),
                group = declaration?.group,
            )
        }
    }
    return targets.sortedWith(compareBy({ it.path }, { it.startLine }))
}

internal fun resolveExpectation(constructs: List<String>, module: ModuleInfo, classification: Classification): Pair<Expectation, Basis> {
    if (constructs.isNotEmpty()) {
        val expectations = constructs.map { classification.constructs[it]?.expectation ?: Expectation.Unspecified }
        val anyOf = expectations.filterIsInstance<Expectation.AnyOf>()
        return when {
            anyOf.isNotEmpty() -> Expectation.AnyOf(anyOf.flatMap { it.categories }.distinct()) to Basis.Construct
            expectations.all { it is Expectation.None } -> expectations.first() to Basis.Construct
            else -> Expectation.Unspecified to Basis.NoExpectation
        }
    }
    module.expectation?.let { return it to Basis.Module }
    return Expectation.Unspecified to if (module.feature != null) Basis.Unclassified else Basis.NoExpectation
}

/** `[3, 4, 5, 9]` → `3–5, 9`. */
fun lineRanges(lines: List<Int>): String {
    if (lines.isEmpty()) return ""
    val ranges = mutableListOf<IntRange>()
    var start = lines.first()
    var previous = start
    for (line in lines.drop(1)) {
        if (line != previous + 1) {
            ranges += start..previous
            start = line
        }
        previous = line
    }
    ranges += start..previous
    return ranges.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" }
}

fun percent(part: Int, whole: Int): String = if (whole == 0) "–" else "${(100.0 * part / whole).let { Math.round(it) }}%"
