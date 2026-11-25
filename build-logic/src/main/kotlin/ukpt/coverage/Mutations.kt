package ukpt.coverage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

enum class MutantStatus(val id: String, val label: String) {
    Killed("killed", "Killed"),
    TimedOut("timed-out", "Timed out"),
    Survived("survived", "Survived"),
    Uncovered("uncovered", "Uncovered"),
    ;

    val detected: Boolean get() = this == Killed || this == TimedOut

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: error("Unknown mutant status \"$id\" in the mutation results.")
    }
}

class Mutant(
    val id: String,
    val path: String,
    val line: Int,
    val operator: String,
    val text: String,
    val status: MutantStatus,
    val killedBy: String?,
    val failure: String?,
    val candidateTests: Int,
)

data class MutantTally(val killed: Int = 0, val survived: Int = 0, val uncovered: Int = 0) {
    val total: Int get() = killed + survived + uncovered

    companion object {
        fun of(mutants: List<Mutant>) = MutantTally(
            killed = mutants.count { it.status.detected },
            survived = mutants.count { it.status == MutantStatus.Survived },
            uncovered = mutants.count { it.status == MutantStatus.Uncovered },
        )
    }
}

/**
 * The last `runMutants` results, from `build/reports/mutation/results.json`. A mutant whose source
 * text no longer starts on its line is stale: the code changed after the run, and the report leaves
 * the mutant out.
 */
class MutationResults(val mutants: List<Mutant>, val stale: Int, val hosts: List<String>, val millis: Long) {
    private val byPath = mutants.groupBy { it.path }

    /** Assigns each mutant to the narrowest target of its file whose range contains its line. */
    fun byTarget(targets: List<Target>): Map<Target, List<Mutant>> {
        val result = mutableMapOf<Target, MutableList<Mutant>>()
        val targetsByPath = targets.groupBy { it.path }
        for ((path, mutants) in byPath) {
            val candidates = targetsByPath[path].orEmpty()
            for (mutant in mutants) {
                val target = candidates.filter { it.declarationName != null && mutant.line in it.startLine..it.endLine }
                    .minByOrNull { it.endLine - it.startLine }
                    ?: candidates.firstOrNull { it.declarationName == null }
                    ?: continue
                result.getOrPut(target) { mutableListOf() } += mutant
            }
        }
        return result
    }

    fun onLine(path: String, line: Int): List<Mutant> = byPath[path].orEmpty().filter { it.line == line }

    companion object {
        fun read(file: File, root: File): MutationResults {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            val sources = mutableMapOf<String, List<String>>()
            var stale = 0
            val mutants = json.getValue("mutants").jsonArray.mapNotNull { element ->
                val m = element.jsonObject
                val mutant = Mutant(
                    id = m.string("id"),
                    path = m.string("path"),
                    line = m.getValue("line").jsonPrimitive.int,
                    operator = m.string("operator"),
                    text = m.string("text"),
                    status = MutantStatus.of(m.string("status")),
                    killedBy = m["killedBy"]?.jsonPrimitive?.contentOrNull,
                    failure = m["failure"]?.jsonPrimitive?.contentOrNull,
                    candidateTests = m["candidateTests"]?.jsonPrimitive?.int ?: 0,
                )
                val lines = sources.getOrPut(mutant.path) { root.resolve(mutant.path).takeIf { it.isFile }?.readLines().orEmpty() }
                if (startsOnLine(mutant, lines)) mutant else { stale++; null }
            }
            val hosts = json["hosts"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val millis = json["summary"]?.jsonObject?.get("millis")?.jsonPrimitive?.content?.toLongOrNull() ?: 0
            return MutationResults(mutants, stale, hosts, millis)
        }

        private fun startsOnLine(mutant: Mutant, lines: List<String>): Boolean {
            if (mutant.line !in 1..lines.size) return false
            val firstLine = WHITESPACE.replace(lines[mutant.line - 1] + " ", " ")
            val following = WHITESPACE.replace(lines.subList(mutant.line - 1, minOf(lines.size, mutant.line + 20)).joinToString(" "), " ")
            return generateSequence(following.indexOf(mutant.text)) { following.indexOf(mutant.text, it + 1) }
                .takeWhile { it in 0 until firstLine.length }
                .any()
        }

        private val WHITESPACE = Regex("\\s+")

        private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    }
}
