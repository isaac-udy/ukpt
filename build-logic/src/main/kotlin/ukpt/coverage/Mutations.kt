package ukpt.coverage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.time.Instant

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
    /** The change the operator made, for example `< → <=`. */
    val variant: String,
    val text: String,
    val status: MutantStatus,
    val killedBy: String?,
    val failure: String?,
    val candidateTests: Int,
    val declaration: String = "",
    val constructs: List<String> = emptyList(),
    val expected: List<String> = emptyList(),
    /** The function or property a `remove-call` mutant removes. */
    val callee: String? = null,
) {
    /** The operator and its change, for example `conditionals-boundary < → <=`. */
    val label: String get() = if (variant.isEmpty()) operator else "$operator $variant"
}

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
 * The last `runMutants` results, from `build/reports/mutation/results.json`, checked against the
 * Kotlin sources the run recorded:
 * - a test file added, changed or deleted after the run makes every result out of date, because a
 *   test in one module can kill mutants in another; [mutants] is then empty and [outdatedReason] says why;
 * - a production file changed after the run drops the file's mutants, counted in [droppedMutants].
 */
class MutationResults(
    val mutants: List<Mutant>,
    val startedAt: Instant?,
    val hosts: List<String>,
    val millis: Long,
    val notSampled: Int = 0,
    val sampleSeed: Long? = null,
    val excludedCalls: Int = 0,
    val droppedMutants: Int = 0,
    val changedProductionFiles: Int = 0,
    val outdatedReason: String? = null,
) {
    private val byPath = mutants.groupBy { it.path }

    val outdated: Boolean get() = outdatedReason != null

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
            val mutants = json.getValue("mutants").jsonArray.map { element ->
                val m = element.jsonObject
                Mutant(
                    id = m.string("id"),
                    path = m.string("path"),
                    line = m.getValue("line").jsonPrimitive.int,
                    operator = m.string("operator"),
                    variant = m.optional("variant").orEmpty(),
                    text = m.string("text"),
                    status = MutantStatus.of(m.string("status")),
                    killedBy = m.optional("killedBy"),
                    failure = m.optional("failure"),
                    candidateTests = m["candidateTests"]?.jsonPrimitive?.int ?: 0,
                    declaration = m.optional("declaration").orEmpty(),
                    constructs = m.strings("constructs"),
                    expected = m.strings("expected"),
                    callee = m.optional("callee"),
                )
            }
            val summary = json["summary"]?.jsonObject
            fun summaryLong(key: String) = summary?.get(key)?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val startedAt = json.optional("startedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val hosts = json["hosts"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            fun results(kept: List<Mutant>, dropped: Int = 0, changed: Int = 0, outdated: String? = null) = MutationResults(
                mutants = kept, startedAt = startedAt, hosts = hosts, millis = summaryLong("millis") ?: 0,
                notSampled = summaryLong("notSampled")?.toInt() ?: 0, sampleSeed = summaryLong("sampleSeed"),
                excludedCalls = summaryLong("excludedCalls")?.toInt() ?: 0,
                droppedMutants = dropped, changedProductionFiles = changed, outdatedReason = outdated,
            )

            val sources = json["sources"]?.jsonObject
                ?: return results(emptyList(), outdated = "the results record no source digest, because an earlier version of runMutants wrote them")
            val roots = sources.getValue("roots").jsonArray.map { it.jsonPrimitive.content }
            val then = sources.getValue("files").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val now = digest(root, roots)
            val changed = (then.keys + now.keys).filter { then[it] != now[it] }
            val (production, tests) = changed.partition(::isProductionSource)
            if (tests.isNotEmpty()) {
                return results(emptyList(), outdated = "${tests.size} test ${if (tests.size == 1) "file" else "files"} changed after the run, for example ${tests.first()}")
            }
            val changedFiles = production.toSet()
            val kept = mutants.filter { it.path !in changedFiles }
            return results(kept, dropped = mutants.size - kept.size, changed = production.size)
        }

        /** The same files and hashes as the mutation plugin's `SourceDigest`. */
        private fun digest(root: File, roots: List<String>): Map<String, String> {
            val files = mutableMapOf<String, String>()
            for (dir in roots) {
                root.resolve(dir).walkTopDown()
                    .onEnter { it.name != "build" && !it.name.startsWith(".") }
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        files[file.relativeTo(root).invariantSeparatorsPath] =
                            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).take(8).joinToString("") { "%02x".format(it) }
                    }
            }
            return files
        }

        /** A file in a `main` or `<name>Main` source set; every other source set holds tests. */
        internal fun isProductionSource(path: String): Boolean {
            val sourceSet = path.substringAfter("/src/", "").substringBefore('/')
            return sourceSet == "main" || sourceSet.endsWith("Main")
        }

        private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
        private fun JsonObject.optional(key: String) = get(key)?.jsonPrimitive?.contentOrNull
        private fun JsonObject.strings(key: String) = get(key)?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    }
}
