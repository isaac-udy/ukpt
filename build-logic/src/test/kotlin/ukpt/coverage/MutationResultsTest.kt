package ukpt.coverage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class MutationResultsTest {

    private val root: File = Files.createTempDirectory("repository").toFile()
    private val path = "feature/a/server/src/main/kotlin/Greet.kt"
    private val module = ModuleInfo(":feature:a:server", "feature/a/server", expectation = null)

    init {
        File(root, path).apply { parentFile.mkdirs() }.writeText(
            """
            package feature.a
            class Greet {
                fun greet(history: List<String>) =
                    if (history.isEmpty()) "Hello"
                    else "Hello again"
                fun isNew(history: List<String>) = history
                    .isEmpty()
            }
            """.trimIndent(),
        )
    }

    private fun results(vararg mutants: String): MutationResults {
        val file = File(root, "results.json")
        file.writeText("""{"hosts": ["jvm"], "summary": {"millis": 1200}, "mutants": [${mutants.joinToString()}]}""")
        return MutationResults.read(file, root)
    }

    private fun mutant(id: String, line: Int, text: String, status: String = "survived") =
        """{"id": "$id", "path": "$path", "line": $line, "operator": "negate-condition", "text": "$text", "status": "$status"}"""

    private fun target(name: String?, startLine: Int, endLine: Int) =
        Target(path, module, name, startLine, endLine, emptyList(), Expectation.Unspecified, Basis.Unclassified, emptyMap())

    @Test
    fun aMutantWhoseTextNoLongerStartsOnItsLineIsStale() {
        val results = results(mutant("a", 4, "history.isEmpty()"), mutant("b", 6, "history .isEmpty()"), mutant("c", 5, "history.isEmpty()"), mutant("d", 3, "history.isEmpty()"))
        assertEquals(listOf("a", "b"), results.mutants.map { it.id })
        assertEquals(2, results.stale)
        assertEquals(listOf("jvm"), results.hosts)
        assertEquals(1200L, results.millis)
    }

    @Test
    fun aMutantBelongsToTheNarrowestDeclarationThatContainsItsLine() {
        val outer = target("feature.a.Greet", 2, 8)
        val greet = target("feature.a.Greet.greet", 3, 5)
        val results = results(mutant("a", 4, "history.isEmpty()", "killed"), mutant("b", 6, "history .isEmpty()"))
        val byTarget = results.byTarget(listOf(outer, greet))
        assertEquals(listOf("a"), byTarget.getValue(greet).map { it.id })
        assertEquals(listOf("b"), byTarget.getValue(outer).map { it.id })
        assertEquals(MutantTally(killed = 1), MutantTally.of(byTarget.getValue(greet)))
    }
}
